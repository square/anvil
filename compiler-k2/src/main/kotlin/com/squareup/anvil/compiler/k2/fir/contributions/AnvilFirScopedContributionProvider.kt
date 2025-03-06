package com.squareup.anvil.compiler.k2.fir.contributions

import com.google.auto.service.AutoService
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.compiler.k2.fir.AnvilFirContext
import com.squareup.anvil.compiler.k2.fir.AnvilFirExtensionFactory
import com.squareup.anvil.compiler.k2.fir.AnvilFirExtensionSessionComponent
import com.squareup.anvil.compiler.k2.fir.cache.lazyWithContext
import com.squareup.anvil.compiler.k2.utils.fir.AnvilPredicates
import com.squareup.anvil.compiler.k2.utils.fir.boundTypeArgumentOrNull
import com.squareup.anvil.compiler.k2.utils.fir.contributesToAnnotations
import com.squareup.anvil.compiler.k2.utils.fir.getContributesBindingAnnotations
import com.squareup.anvil.compiler.k2.utils.fir.rankArgumentOrNull
import com.squareup.anvil.compiler.k2.utils.fir.replacesArgumentOrNull
import com.squareup.anvil.compiler.k2.utils.fir.requireAnnotation
import com.squareup.anvil.compiler.k2.utils.fir.requireArgumentAt
import com.squareup.anvil.compiler.k2.utils.fir.requireClassId
import com.squareup.anvil.compiler.k2.utils.fir.requireScopeArgument
import com.squareup.anvil.compiler.k2.utils.fir.requireTargetClassId
import com.squareup.anvil.compiler.k2.utils.fir.resolveConeType
import com.squareup.anvil.compiler.k2.utils.names.ClassIds
import com.squareup.anvil.compiler.k2.utils.names.FqNames
import com.squareup.anvil.compiler.k2.utils.names.Names
import com.squareup.anvil.compiler.k2.utils.names.bindingModuleSibling
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.caches.FirCache
import org.jetbrains.kotlin.fir.caches.contains
import org.jetbrains.kotlin.fir.caches.firCachesFactory
import org.jetbrains.kotlin.fir.caches.getValue
import org.jetbrains.kotlin.fir.declarations.evaluateAs
import org.jetbrains.kotlin.fir.declarations.unwrapVarargValue
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.FirExtensionSessionComponent
import org.jetbrains.kotlin.fir.extensions.FirSupertypeGenerationExtension
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.resolve.getSuperTypes
import org.jetbrains.kotlin.fir.resolve.providers.dependenciesSymbolProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.utils.checkWithAttachment
import org.jetbrains.kotlin.utils.sure

public val FirSession.anvilFirScopedContributionProvider: AnvilFirScopedContributionProvider
  by FirSession.sessionComponentAccessor()

public class AnvilFirScopedContributionProvider(
  anvilFirContext: AnvilFirContext,
  session: FirSession,
) : AnvilFirExtensionSessionComponent(anvilFirContext, session) {

  @AutoService(AnvilFirExtensionFactory::class)
  public class Factory : AnvilFirExtensionSessionComponent.Factory {
    override fun create(anvilFirContext: AnvilFirContext): FirExtensionSessionComponent.Factory {
      return FirExtensionSessionComponent.Factory { session ->
        AnvilFirScopedContributionProvider(anvilFirContext, session)
      }
    }
  }

  private val cachesFactory = session.firCachesFactory

  public val contributesBindingSymbols: List<FirClassLikeSymbol<*>> by cachesFactory.createLazyValue {
    session.predicateBasedProvider.getSymbolsByPredicate(AnvilPredicates.hasAnvilContributesBinding)
      .filterIsInstance<FirClassLikeSymbol<*>>()
  }

  public val contributesToSymbols: List<FirClassLikeSymbol<*>> by cachesFactory.createLazyValue {
    session.predicateBasedProvider.getSymbolsByPredicate(AnvilPredicates.hasAnvilContributesTo)
      .filterIsInstance<FirClassLikeSymbol<*>>()
  }
  public val contributedModuleSymbols: List<FirClassLikeSymbol<*>> by cachesFactory.createLazyValue {
    contributesToSymbols.filter {
      session.predicateBasedProvider.matches(AnvilPredicates.contributedModule, it)
    }
  }

  public val initialized: Boolean get() = allScopedContributions.isInitialized()

  private val allScopedContributions =
    lazyWithContext { typeResolveService: FirSupertypeGenerationExtension.TypeResolveService ->
      initList(typeResolveService)
    }

  private val contributionsByScope:
    FirCache<ClassId, List<ScopedContribution>, FirSupertypeGenerationExtension.TypeResolveService> =
    session.firCachesFactory.createCache { scopeType, typeResolver ->
      getContributions(typeResolver).filter { it.scopeType == scopeType }
    }

  public fun getContributions(): List<ScopedContribution> {
    checkWithAttachment(
      allScopedContributions.isInitialized(),
      {
        val resolverName = FirSupertypeGenerationExtension.TypeResolveService::class.simpleName
        "Scoped contributions have not been computed yet.  " +
          "Call the overloaded version with a $resolverName first."
      },
    )
    return allScopedContributions.getValue()
  }

  public fun getContributions(
    typeResolveService: FirSupertypeGenerationExtension.TypeResolveService,
  ): List<ScopedContribution> = allScopedContributions.getValue(typeResolveService)

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    register(AnvilPredicates.contributedModule)
    register(AnvilPredicates.hasAnvilContributesTo)
  }

  public fun getContributionsForScope(
    scopeType: ClassId,
    typeResolveService: FirSupertypeGenerationExtension.TypeResolveService,
  ): List<ScopedContribution> = contributionsByScope.getValue(scopeType, typeResolveService)

  public fun getContributionsForScope(scopeType: ClassId): List<ScopedContribution> {
    checkWithAttachment(
      contributionsByScope.contains(scopeType),
      {
        val resolverName = FirSupertypeGenerationExtension.TypeResolveService::class.simpleName
        "Scoped contributions for scope `$scopeType` have not been computed yet.  " +
          "Call the overloaded version with a $resolverName first."
      },
    ) {
      it.withAttachment("scopeType", scopeType)
    }
    return contributionsByScope.getValueIfComputed(scopeType).orEmpty()
  }

  private val dependencyHintClassSymbols by cachesFactory.createLazyValue {
    session.dependenciesSymbolProvider
      .symbolNamesProvider.getTopLevelClassifierNamesInPackage(FqNames.anvilHintPackage)
      .orEmpty()
      .mapNotNull {
        session.dependenciesSymbolProvider
          .getClassLikeSymbolByClassId(ClassId(FqNames.anvilHintPackage, it))
      }
  }

  private fun initList(
    typeResolveService: FirSupertypeGenerationExtension.TypeResolveService,
  ): List<ScopedContribution> {

    val contributedModulesInDependencies = dependencyHintClassSymbols
      .flatMap { clazzSymbol ->

        val annotation = clazzSymbol.requireAnnotation(
          classId = ClassIds.anvilInternalContributedModule,
          session = session,
        )

        val arg = annotation.requireArgumentAt(
          name = Names.hints,
          index = 1,
          unwrapNamedArguments = true,
        )

        val argStrings = arg.unwrapVarargValue()
          .map {
            it.evaluateAs<FirLiteralExpression>(session)
              .sure { "can't evaluate expression: $it" }.value as String
          }

        argStrings.map { line ->
          val allNames = line.splitToSequence('|')
            .map { ClassId.topLevel(FqName(it.trim())) }
          allNames.first() to allNames.drop(1)
        }
      }.map { (moduleFqName, replaces) ->
        ContributedModule(
          scopeType = session.builtinTypes.unitType.id,
          contributedType = moduleFqName,
          replaces = replaces.toList(),
        )
      }

    val fromSession = contributesToSymbols
      .flatMap { clazz -> clazz.getContributesTo(typeResolveService) }

    val generated = session.contributesBindingSessionComponent
      .generatedIdsToMatchedSymbols
      .flatMap { (moduleClassId, symbol) ->
        getContributedBinding(symbol, moduleClassId, typeResolveService)
      }

    return contributedModulesInDependencies + fromSession + generated
  }

  private fun getContributedBinding(
    matchedSymbol: FirRegularClassSymbol,
    moduleClassId: ClassId,
    typeResolveService: FirSupertypeGenerationExtension.TypeResolveService,
  ): List<ScopedContribution> = matchedSymbol.getContributesBindingAnnotations(this.session)
    .flatMap { annotation ->

      val boundType = cachesFactory.createLazyValue {
        annotation.boundTypeArgumentOrNull(session)
          ?.resolveConeType(typeResolveService)
          ?.requireClassId()
          ?: matchedSymbol.getSuperTypes(
            useSiteSession = session,
            recursive = false,
            lookupInterfaces = true,
          )
            .single()
            .requireClassId()
      }

      listOf(
        ContributedBinding(
          scopeType = annotation.requireScopeArgument(typeResolveService).requireClassId(),
          boundType = boundType,
          contributedType = matchedSymbol.classId,
          replaces = annotation.replacesClassIds(),
          rank = annotation.rankArgumentOrNull(session) ?: ContributesBinding.RANK_NORMAL,
          ignoreQualifier = false,
          isMultibinding = false,
          bindingModule = matchedSymbol.classId.bindingModuleSibling,
          qualifier = null,
        ),
        ContributedModule(
          scopeType = annotation.requireScopeArgument(typeResolveService).requireClassId(),
          contributedType = moduleClassId,
          replaces = annotation.replacesClassIds(),
        ),
      )
    }

  private fun FirClassLikeSymbol<*>.getContributesTo(
    typeResolveService: FirSupertypeGenerationExtension.TypeResolveService,
  ): List<ContributedTo> {
    val predicateMatcher = session.predicateBasedProvider
    val clazz = this@getContributesTo

    return contributesToAnnotations(session).map { annotation ->

      val scopeType = annotation.requireScopeArgument(typeResolveService).requireClassId()

      if (predicateMatcher.matches(AnvilPredicates.hasModuleAnnotation, clazz)) {
        ContributedModule(
          scopeType = scopeType,
          contributedType = classId,
          replaces = annotation.replacesClassIds(),
        )
      } else {
        ContributedSupertype(
          scopeType = scopeType,
          contributedType = classId,
          replaces = annotation.replacesClassIds(),
        )
      }
    }
  }

  private fun FirAnnotationCall.replacesClassIds() = replacesArgumentOrNull(session)
    ?.map { it.requireTargetClassId() }
    .orEmpty()
}
