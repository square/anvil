package com.squareup.anvil.compiler.k2.fir.merging

import com.google.auto.service.AutoService
import com.squareup.anvil.compiler.k2.fir.AnvilFirContext
import com.squareup.anvil.compiler.k2.fir.AnvilFirExtensionFactory
import com.squareup.anvil.compiler.k2.fir.AnvilFirSupertypeGenerationExtension
import com.squareup.anvil.compiler.k2.fir.abstraction.providers.RequiresTypesResolutionPhase
import com.squareup.anvil.compiler.k2.fir.abstraction.providers.anvilFirDependencyHintProvider
import com.squareup.anvil.compiler.k2.fir.abstraction.providers.scopedContributionProvider
import com.squareup.anvil.compiler.k2.utils.fir.AnvilPredicates
import com.squareup.anvil.compiler.k2.utils.fir.argumentAt
import com.squareup.anvil.compiler.k2.utils.fir.classListArgumentAt
import com.squareup.anvil.compiler.k2.utils.fir.requireClassId
import com.squareup.anvil.compiler.k2.utils.fir.requireClassLikeSymbol
import com.squareup.anvil.compiler.k2.utils.fir.requireScopeArgument
import com.squareup.anvil.compiler.k2.utils.fir.resolveConeType
import com.squareup.anvil.compiler.k2.utils.fir.setAnnotationType
import com.squareup.anvil.compiler.k2.utils.fir.toGetClassCall
import com.squareup.anvil.compiler.k2.utils.names.ClassIds
import com.squareup.anvil.compiler.k2.utils.names.Names
import com.squareup.anvil.compiler.k2.utils.psi.ktPsiFactory
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
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtCollectionLiteralExpression
import org.jetbrains.kotlin.toKtPsiSourceElement

/**
 * This extension merges all contributed Dagger modules on the classpath and includes them on the
 * component annotated with `@MergeComponent`.
 */
public class AnvilFirAnnotationMergingExtension(
  anvilFirContext: AnvilFirContext,
  session: FirSession,
) : AnvilFirSupertypeGenerationExtension(anvilFirContext, session) {

  @AutoService(AnvilFirExtensionFactory::class)
  public class Factory : AnvilFirSupertypeGenerationExtension.Factory {
    override fun create(anvilFirContext: AnvilFirContext): FirSupertypeGenerationExtension.Factory {
      return FirSupertypeGenerationExtension.Factory { session ->
        AnvilFirAnnotationMergingExtension(anvilFirContext, session)
      }
    }
  }

  private companion object {
    private val PREDICATE = DeclarationPredicate.create {
      annotated(ClassIds.anvilMergeComponent.asSingleFqName())
    }
  }

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    register(AnvilPredicates.hasAnvilMergeComponent)
    register(AnvilPredicates.contributedModule)
  }

  @OptIn(RequiresTypesResolutionPhase::class)
  override fun computeAdditionalSupertypes(
    classLikeDeclaration: FirClassLikeDeclaration,
    resolvedSupertypes: List<FirResolvedTypeRef>,
    typeResolver: TypeResolveService,
  ): List<ConeKotlinType> {

    val componentAnnotation = classLikeDeclaration.annotations
      .single { it.fqName(session) == ClassIds.anvilMergeComponent.asSingleFqName() } as FirAnnotationCall

    val mergeScope = componentAnnotation.requireScopeArgument().resolveConeType(typeResolver)
    val mergeScopeId = mergeScope.requireClassId()

    val oldModules = componentAnnotation
      .classListArgumentAt(Names.modules, index = 1)
      .orEmpty()

    classLikeDeclaration.replaceAnnotations(
      classLikeDeclaration.annotations + buildAnnotationCallCopy(componentAnnotation) {
        setAnnotationType(
          newType = ClassIds.daggerComponent,
          ktPsiFactoryOrNull = classLikeDeclaration.psi?.ktPsiFactory(),
        )

        val sourceModules = session.scopedContributionProvider.contributedModules
        val generatedModules = session.scopedContributionProvider.contributedBindingModules
        val dependencyModules = session.anvilFirDependencyHintProvider.getContributions()

        val contributedModules = (sourceModules + generatedModules + dependencyModules)
          .filter { it.scopeType.getValue() == mergeScopeId }
          .map { it.contributedType }
          .sortedBy { it.asString() }

        val mergedModules = contributedModules.map { it.asSingleFqName() }

        val newAnnotationCallPsi = componentAnnotation.psi?.let {
          buildNewAnnotationPsi(
            oldAnnotationCall = it as KtAnnotationEntry,
            mergedModules = mergedModules,
          )
        }

        val newSource =
          newAnnotationCallPsi?.toKtPsiSourceElement(KtFakeSourceElementKind.PluginGenerated)
            ?: componentAnnotation.source?.fakeElement(KtFakeSourceElementKind.PluginGenerated)
        source = newSource

        argumentList = buildArgumentList {
          source = componentAnnotation.source?.fakeElement(KtFakeSourceElementKind.PluginGenerated)

          arguments += buildNamedArgumentExpression {
            name = Names.modules
            isSpread = false
            expression = buildArrayLiteral {
              argumentList = buildArgumentList {
                arguments += oldModules
                arguments += contributedModules.map {
                  it.requireClassLikeSymbol(session).toGetClassCall()
                }
              }
            }
          }

          componentAnnotation.argumentAt(
            Names.dependencies,
            index = 2,
            unwrapNamedArguments = false,
          )?.let {
            arguments += it
          }
        }
      },
    )

    return emptyList()
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

    val imports = oldAnnotationCall.containingKtFile.importDirectives
      .associate { imp ->
        val fqName = imp.importedReference?.text
          ?: error("import directive doesn't have a reference? $imp")

        val name = imp.aliasName
          ?: imp.importedReference?.text?.substringAfterLast('.')
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

    val newModulesText = "modules = [$classArgList]"

    val componentCall = ClassIds.daggerComponent.asFqNameString().let { fqString ->
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

    // session.anvilFirScopedContributionProvider.getContributions()

    return session.predicateBasedProvider.matches(PREDICATE, declaration)
  }
}
