package com.squareup.anvil.compiler.k2.fir.merging

import com.google.auto.service.AutoService
import com.squareup.anvil.compiler.k2.fir.AnvilFirContext2
import com.squareup.anvil.compiler.k2.fir.AnvilFirProcessor
import com.squareup.anvil.compiler.k2.fir.FlushingSupertypeProcessor
import com.squareup.anvil.compiler.k2.fir.RequiresTypesResolutionPhase
import com.squareup.anvil.compiler.k2.fir.abstraction.providers.anvilFirDependencyHintProvider
import com.squareup.anvil.compiler.k2.fir.abstraction.providers.anvilFirSymbolProvider
import com.squareup.anvil.compiler.k2.fir.abstraction.providers.scopedContributionProvider
import com.squareup.anvil.compiler.k2.utils.fir.createClassListArgument
import com.squareup.anvil.compiler.k2.utils.fir.createFirAnnotationCall
import com.squareup.anvil.compiler.k2.utils.names.ClassIds
import com.squareup.anvil.compiler.k2.utils.names.Names
import com.squareup.anvil.compiler.k2.utils.psi.ktPsiFactory
import com.squareup.anvil.compiler.k2.utils.stdlib.mapToSet
import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.fakeElement
import org.jetbrains.kotlin.fir.caches.getValue
import org.jetbrains.kotlin.fir.declarations.FirClassLikeDeclaration
import org.jetbrains.kotlin.fir.declarations.utils.classId
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.expressions.builder.buildArgumentList
import org.jetbrains.kotlin.fir.psi
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtCollectionLiteralExpression
import org.jetbrains.kotlin.toKtPsiSourceElement

@AutoService(AnvilFirProcessor.Factory::class)
public class AnnotationMergingGeneratorFactory : AnvilFirProcessor.Factory {
  override fun create(anvilFirContext: AnvilFirContext2): AnvilFirProcessor {
    return AnnotationMergingGenerator(anvilFirContext)
  }
}

/**
 * This generator merges all contributed Dagger modules on the classpath and includes them on the
 * component annotated with `@MergeComponent`.
 */
public class AnnotationMergingGenerator(
  override val anvilFirContext: AnvilFirContext2,
) : FlushingSupertypeProcessor() {

  private val mergedComponentIds by lazyValue {
    session.anvilFirSymbolProvider.mergeComponentSymbols.mapToSet { it.classId }
  }

  @RequiresTypesResolutionPhase
  private val mergedModulesByScope by lazyValue {
    val sourceModules = session.scopedContributionProvider.contributedModules
    val generatedModules = session.scopedContributionProvider.contributedBindingModules
    val dependencyModules = session.anvilFirDependencyHintProvider.allDependencyContributedModules

    (sourceModules + generatedModules + dependencyModules).groupBy { it.scopeType.getValue() }
  }

  override fun shouldProcess(declaration: FirClassLikeDeclaration): Boolean =
    declaration.classId in mergedComponentIds

  @RequiresTypesResolutionPhase
  public override fun generateAnnotation(
    classLikeDeclaration: FirClassLikeDeclaration,
  ): FirAnnotationCall {
    val mergedComponent =
      session.scopedContributionProvider.mergedComponents
        .single { it.containingDeclaration.getValue().classId == classLikeDeclaration.classId }

    val mergeScopeId = mergedComponent.scopeType.getValue()

    val containingDeclaration = mergedComponent.containingDeclaration.getValue()

    val annotationModules = mergedComponent.modules.getValue()

    val mergedModules = mergedModulesByScope[mergeScopeId].orEmpty()
      .map { it.contributedType }
      .plus(annotationModules)
      .sortedBy { it.asString() }

    val mergeAnnotation = mergedComponent.mergeAnnotationCall.getValue()

    val newAnnotationCallPsi = mergeAnnotation.psi?.let { psiEntry ->
      buildNewAnnotationPsi(
        oldAnnotationCall = psiEntry as KtAnnotationEntry,
        mergedModules = mergedModules,
      )
    }

    val newSource =
      newAnnotationCallPsi?.toKtPsiSourceElement(KtFakeSourceElementKind.PluginGenerated)
        ?: mergeAnnotation.source?.fakeElement(KtFakeSourceElementKind.PluginGenerated)

    return createFirAnnotationCall(
      type = ClassIds.daggerComponent,
      containingDeclarationSymbol = containingDeclaration.symbol,
      argumentList = buildArgumentList {

        source = mergeAnnotation.source?.fakeElement(KtFakeSourceElementKind.PluginGenerated)

        if (mergedModules.isNotEmpty()) {
          arguments += createClassListArgument(Names.modules, mergedModules, session)
        }

        val mergeDeps = mergedComponent.dependencies.getValue()
        if (mergeDeps.isEmpty()) {
          arguments += createClassListArgument(Names.dependencies, mergeDeps, session)
        }
      },
      source = newSource,
      ktPsiFactory = containingDeclaration.psi?.ktPsiFactory(),
    )
  }

  private fun buildNewAnnotationPsi(
    oldAnnotationCall: KtAnnotationEntry,
    mergedModules: List<ClassId>,
  ): KtAnnotationEntry {

    val oldAnnotationArguments = oldAnnotationCall.valueArgumentList
      ?.arguments
      .orEmpty()

    // `modules = [SomeModule::class]`
    val oldModulesArg = oldAnnotationArguments
      // `Component` is a Java annotation with default argument values,
      // so its arguments can be missing or in any order, but they must be named if they're present.
      .firstOrNull { arg ->
        val name = arg.getArgumentName()
        name == null || name.text == "modules"
      }

    // `SomeModule::class, SomeOtherModule::class`
    val existingModuleArgExpressions =
      (oldModulesArg?.getArgumentExpression() as? KtCollectionLiteralExpression)
        ?.innerExpressions
        ?.map { it.text }
        .orEmpty()

    val imports = oldAnnotationCall.containingKtFile.importDirectives
      .associate { imp ->
        val fqName = imp.importedReference?.text
          ?: error("import directive doesn't have a reference? $imp")

        val name = imp.aliasName
          ?: imp.importedReference?.text?.substringAfterLast('.')
          ?: error("import directive doesn't have a reference or alias? ${imp.text}")

        fqName to name
      }

    val newModulesMaybeImported = mergedModules.map { it.asFqNameString() }
      .map { moduleFqName ->
        imports[moduleFqName] ?: moduleFqName
      }

    val allClassArgs = existingModuleArgExpressions
      .plus(newModulesMaybeImported.map { "$it::class" })
      .distinct()

    val factory = oldAnnotationCall.ktPsiFactory()

    val classArgList = allClassArgs.joinToString(separator = ", ")

    val newModulesText = "modules = [$classArgList]"

    val componentCall = ClassIds.daggerComponent.asFqNameString().let { fqString ->
      imports[fqString] ?: fqString
    }

    val newAnnotationText = when {
      oldAnnotationArguments.isEmpty() -> "@$componentCall($newModulesText)"
      oldModulesArg != null -> oldAnnotationCall.text.replace(
        oldValue = oldModulesArg.text,
        newValue = newModulesText,
      )
      else -> oldAnnotationArguments.map { it.text }
        .plus(newModulesText)
        .joinToString(
          separator = ",\n",
          prefix = "@$componentCall(\n",
          postfix = "\n)",
        ) { "  $it" }
    }

    return factory.createAnnotationEntry(newAnnotationText)
  }
}
