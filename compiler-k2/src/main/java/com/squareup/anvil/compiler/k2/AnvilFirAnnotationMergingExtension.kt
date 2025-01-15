package com.squareup.anvil.compiler.k2

import com.squareup.anvil.compiler.internal.ktFile
import com.squareup.anvil.compiler.k2.internal.AnvilPredicates
import com.squareup.anvil.compiler.k2.internal.Names
import com.squareup.anvil.compiler.k2.internal.argumentAt
import com.squareup.anvil.compiler.k2.internal.buildGetClassCall
import com.squareup.anvil.compiler.k2.internal.classId
import com.squareup.anvil.compiler.k2.internal.classListArgumentAt
import com.squareup.anvil.compiler.k2.internal.ktPsiFactory
import com.squareup.anvil.compiler.k2.internal.requireScopeArgument
import com.squareup.anvil.compiler.k2.internal.resolveConeType
import com.squareup.anvil.compiler.k2.internal.setAnnotationType
import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.fakeElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirClassLikeDeclaration
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotationCallCopy
import org.jetbrains.kotlin.fir.expressions.builder.buildArgumentList
import org.jetbrains.kotlin.fir.expressions.builder.buildArrayLiteral
import org.jetbrains.kotlin.fir.expressions.builder.buildNamedArgumentExpression
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.FirSupertypeGenerationExtension
import org.jetbrains.kotlin.fir.extensions.predicate.DeclarationPredicate
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.psi
import org.jetbrains.kotlin.fir.resolve.fqName
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
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

    val componentAnnotation = classLikeDeclaration.annotations
      .single { it.fqName(session) == Names.anvil.mergeComponent } as FirAnnotationCall

    val scope = componentAnnotation.requireScopeArgument().resolveConeType(typeResolver)

    val oldModules = componentAnnotation.classListArgumentAt(Name.identifier("modules"), index = 1)
      .orEmpty()

    classLikeDeclaration.replaceAnnotations(
      classLikeDeclaration.annotations + buildAnnotationCallCopy(componentAnnotation) {
        setAnnotationType(
          newType = Names.dagger.component,
          ktPsiFactoryOrNull = classLikeDeclaration.psi?.ktPsiFactory(),
        )

        // TODO: This is a dummy module, replace it with the actual parsed values
        val newModules = listOf(Names.foo.bBindingModule)

        val packageNames = listOf(Names.foo.packageFqName)

        session.predicateBasedProvider.getSymbolsByPredicate(AnvilPredicates.contributedModule)
          .forEach { moduleSymbol ->

            println("########### moduleSymbol: $moduleSymbol")
          }

        val lotsOfThings = packageNames.flatMap { packageFqName ->

          session.symbolProvider.symbolNamesProvider
            .getTopLevelClassifierNamesInPackage(packageFqName).orEmpty()
            .map { packageFqName.child(it) }
        }
          .map { className ->
            session.symbolProvider.getClassLikeSymbolByClassId(className.classId())
          }

        val newAnnotationCallPsi = componentAnnotation.psi?.let {
          buildNewAnnotationPsi(
            oldAnnotationCall = it as KtAnnotationEntry,
            mergedModules = newModules,
          )
        }

        val newSource =
          newAnnotationCallPsi?.toKtPsiSourceElement(KtFakeSourceElementKind.PluginGenerated)
            ?: componentAnnotation.source?.fakeElement(KtFakeSourceElementKind.PluginGenerated)
        source = newSource

        argumentList = buildArgumentList {
          source = componentAnnotation.source?.fakeElement(KtFakeSourceElementKind.PluginGenerated)

          arguments += buildNamedArgumentExpression {
            name = Name.identifier("modules")
            isSpread = false
            expression = buildArrayLiteral {
              argumentList = buildArgumentList {
                arguments += oldModules
                arguments += newModules.map {

                  val newModuleClassSymbol = session.symbolProvider
                    .getClassLikeSymbolByClassId(it.classId())

                  requireNotNull(newModuleClassSymbol) {
                    "Couldn't find symbol for ${it.classId()}"
                  }

                  buildGetClassCall(newModuleClassSymbol)
                }
              }
            }
          }

          componentAnnotation.argumentAt(name = "dependencies", index = 2)?.let {
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
