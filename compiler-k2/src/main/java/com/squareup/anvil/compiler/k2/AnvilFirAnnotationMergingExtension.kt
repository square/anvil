package com.squareup.anvil.compiler.k2

import com.squareup.anvil.compiler.internal.asClassName
import com.squareup.anvil.compiler.internal.joinSimpleNames
import com.squareup.anvil.compiler.internal.ktFile
import com.squareup.anvil.compiler.internal.reference.asClassId
import com.squareup.anvil.compiler.k2.internal.AnvilPredicates
import com.squareup.anvil.compiler.k2.internal.Names
import com.squareup.anvil.compiler.k2.internal.argumentAt
import com.squareup.anvil.compiler.k2.internal.classId
import com.squareup.anvil.compiler.k2.internal.classListArgumentAt
import com.squareup.anvil.compiler.k2.internal.contributesToScope
import com.squareup.anvil.compiler.k2.internal.fqName
import com.squareup.anvil.compiler.k2.internal.ktPsiFactory
import com.squareup.anvil.compiler.k2.internal.requireFqName
import com.squareup.anvil.compiler.k2.internal.requireScopeArgument
import com.squareup.anvil.compiler.k2.internal.resolveConeType
import com.squareup.anvil.compiler.k2.internal.setAnnotationType
import com.squareup.anvil.compiler.k2.internal.toGetClassCall
import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.fakeElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirClassLikeDeclaration
import org.jetbrains.kotlin.fir.declarations.utils.classId
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotationCallCopy
import org.jetbrains.kotlin.fir.expressions.builder.buildArgumentList
import org.jetbrains.kotlin.fir.expressions.builder.buildArrayLiteral
import org.jetbrains.kotlin.fir.expressions.builder.buildNamedArgumentExpression
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.FirSupertypeGenerationExtension
import org.jetbrains.kotlin.fir.extensions.predicate.DeclarationPredicate
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.plugin.createConeType
import org.jetbrains.kotlin.fir.psi
import org.jetbrains.kotlin.fir.resolve.fqName
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtCollectionLiteralExpression
import org.jetbrains.kotlin.toKtPsiSourceElement

public class AnvilFirAnnotationMergingExtension(session: FirSession) :
  FirSupertypeGenerationExtension(session) {

  private companion object {
    private val annotationClassId = Names.anvil.mergeComponent.classId()
    private val PREDICATE = DeclarationPredicate.create {
      annotated(annotationClassId.asSingleFqName())
    }
  }

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    register(AnvilPredicates.hasMergeComponentFirAnnotation)
    register(AnvilPredicates.contributedModule)
  }

  override fun computeAdditionalSupertypes(
    classLikeDeclaration: FirClassLikeDeclaration,
    resolvedSupertypes: List<FirResolvedTypeRef>,
    typeResolver: TypeResolveService,
  ): List<ConeKotlinType> {
    // val bindingModules = session.predicateBasedProvider
    //   .getSymbolsByPredicate(AnvilPredicates.hasContributesBindingAnnotation)
    //   .filterIsInstance<FirRegularClassSymbol>()
    //   .mapNotNull { contributedClass ->
    //     contributedClass.classId.asClassName()
    //       .joinSimpleNames(suffix = "_BindingModule")
    //       .asClassId()
    //       .createConeType(session)
    //   }
    val componentAnnotation = classLikeDeclaration.annotations
      .single { it.fqName(session) == Names.anvil.mergeComponent } as FirAnnotationCall

    val scope = componentAnnotation.requireScopeArgument().resolveConeType(typeResolver)
    val scopeFqName = scope.requireFqName()

    val oldModules = componentAnnotation.classListArgumentAt(Names.identifiers.modules, index = 1)
      .orEmpty()

    classLikeDeclaration.replaceAnnotations(
      classLikeDeclaration.annotations + buildAnnotationCallCopy(componentAnnotation) {
        setAnnotationType(
          newType = Names.dagger.component,
          ktPsiFactoryOrNull = classLikeDeclaration.psi?.ktPsiFactory(),
        )

        val contributedModules = session.predicateBasedProvider
          .getSymbolsByPredicate(AnvilPredicates.contributedModule)
          .mapNotNull { moduleClass ->
            if (moduleClass !is FirClassLikeSymbol<*>) return@mapNotNull null

            if (moduleClass.contributesToScope(
                scope = scopeFqName,
                session = session,
                typeResolveService = typeResolver,
              )
            ) {
              moduleClass
            } else {
              null
            }
          }

        val newAnnotationCallPsi = componentAnnotation.psi?.let {
          buildNewAnnotationPsi(
            oldAnnotationCall = it as KtAnnotationEntry,
            mergedModules = contributedModules.map { it.fqName() },
          )
        }

        val newSource =
          newAnnotationCallPsi?.toKtPsiSourceElement(KtFakeSourceElementKind.PluginGenerated)
            ?: componentAnnotation.source?.fakeElement(KtFakeSourceElementKind.PluginGenerated)
        source = newSource

        argumentList = buildArgumentList {
          source = componentAnnotation.source?.fakeElement(KtFakeSourceElementKind.PluginGenerated)

          arguments += buildNamedArgumentExpression {
            name = Names.identifiers.modules
            isSpread = false
            expression = buildArrayLiteral {
              argumentList = buildArgumentList {
                arguments += oldModules
                arguments += contributedModules.map { it.toGetClassCall() }
                // + contributedModules.map { it.toGetClassCall() }
              }
            }
          }

          componentAnnotation.argumentAt(
            Names.identifiers.dependencies,
            index = 2,
            unwrapNamedArguments = false,
          )?.let {
            arguments += it
          }
        }
      },
    )

    return listOf()
  }

  private fun buildNewAnnotationPsi(
    oldAnnotationCall: KtAnnotationEntry,
    mergedModules: List<FqName>,
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

    val imports = oldAnnotationCall.ktFile().importDirectives
      .associate { imp ->
        val fqName = imp.importedReference?.text
          ?: error("import directive doesn't have a reference? $imp")

        val name = imp.aliasName ?: imp.importedReference?.text?.substringAfterLast('.')
          ?: error("import directive doesn't have a reference or alias? ${imp.text}")

        fqName to name
      }

    val newModulesMaybeImported = mergedModules.map { it.asString() }
      .map { moduleFqName ->
        imports[moduleFqName] ?: moduleFqName
      }

    val allClassArgs = existingModuleArgExpressions
      .plus(newModulesMaybeImported.map { "$it::class" })
      .distinct()

    val factory = oldAnnotationCall.ktPsiFactory()

    val classArgList = allClassArgs.joinToString(separator = ", ")

    val newModulesText = when {
      oldModulesArg == null -> "modules = [$classArgList]"
      existingModuleArgExpressions.isEmpty() -> "modules = [$classArgList]"
      else -> "modules = [$classArgList]"
    }

    val componentCall = Names.dagger.component.asString().let { fqString ->
      imports[fqString] ?: fqString
    }

    val newAnnotationText = when {
      oldAnnotationArguments.isEmpty() -> "@$componentCall($newModulesText)"
      oldModulesArg != null -> oldAnnotationCall.text.replace(oldModulesArg.text, newModulesText)
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

  override fun needTransformSupertypes(declaration: FirClassLikeDeclaration): Boolean {
    return session.predicateBasedProvider.matches(PREDICATE, declaration)
  }
}
