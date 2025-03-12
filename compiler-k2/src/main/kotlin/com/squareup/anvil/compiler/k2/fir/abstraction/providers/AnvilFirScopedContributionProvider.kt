package com.squareup.anvil.compiler.k2.fir.abstraction.providers

import com.google.auto.service.AutoService
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.compiler.k2.fir.AnvilFirContext
import com.squareup.anvil.compiler.k2.fir.AnvilFirExtensionFactory
import com.squareup.anvil.compiler.k2.fir.AnvilFirExtensionSessionComponent
import com.squareup.anvil.compiler.k2.fir.cache.lazyWithContext
import com.squareup.anvil.compiler.k2.fir.contributions.ContributedBinding
import com.squareup.anvil.compiler.k2.fir.contributions.ContributedModule
import com.squareup.anvil.compiler.k2.fir.contributions.ContributedSupertype
import com.squareup.anvil.compiler.k2.fir.contributions.ContributedTo
import com.squareup.anvil.compiler.k2.fir.contributions.ScopedContribution
import com.squareup.anvil.compiler.k2.utils.fir.AnvilPredicates
import com.squareup.anvil.compiler.k2.utils.fir.boundTypeArgumentOrNull
import com.squareup.anvil.compiler.k2.utils.fir.contributesToAnnotations
import com.squareup.anvil.compiler.k2.utils.fir.getContributesBindingAnnotations
import com.squareup.anvil.compiler.k2.utils.fir.rankArgumentOrNull
import com.squareup.anvil.compiler.k2.utils.fir.replacesArgumentOrNull
import com.squareup.anvil.compiler.k2.utils.fir.requireClassId
import com.squareup.anvil.compiler.k2.utils.fir.requireScopeArgument
import com.squareup.anvil.compiler.k2.utils.fir.requireTargetClassId
import com.squareup.anvil.compiler.k2.utils.fir.resolveConeType
import com.squareup.anvil.compiler.k2.utils.names.bindingModuleSibling
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.caches.FirLazyValue
import org.jetbrains.kotlin.fir.caches.firCachesFactory
import org.jetbrains.kotlin.fir.caches.getValue
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.FirExtensionSessionComponent
import org.jetbrains.kotlin.fir.extensions.FirSupertypeGenerationExtension
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.resolve.getSuperTypes
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.utils.exceptions.checkWithAttachment

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

  public val contributesBindingSymbols: List<FirRegularClassSymbol> by createLazyValue {
    session.predicateBasedProvider.getSymbolsByPredicate(AnvilPredicates.hasAnvilContributesBinding)
      .filterIsInstance<FirRegularClassSymbol>()
  }

  public val contributesToSymbols: List<FirRegularClassSymbol> by createLazyValue {
    session.predicateBasedProvider.getSymbolsByPredicate(AnvilPredicates.hasAnvilContributesTo)
      .filterIsInstance<FirRegularClassSymbol>()
  }

  public val contributedModuleSymbols: List<FirRegularClassSymbol> by createLazyValue {
    contributesToSymbols.filter {
      session.predicateBasedProvider.matches(AnvilPredicates.contributedModule, it)
    }
  }

  private val allScopedContributions =
    lazyWithContext { typeResolveService: FirSupertypeGenerationExtension.TypeResolveService ->
      initList(typeResolveService)
    }

  private fun <V> createLazyValue(createValue: () -> V): FirLazyValue<V> =
    cachesFactory.createLazyValue(createValue)

  public fun isInitialized(): Boolean = allScopedContributions.isInitialized()

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

  private fun initList(
    typeResolver: FirSupertypeGenerationExtension.TypeResolveService,
  ): List<ScopedContribution> {

    val fromSession = contributesToSymbols
      .flatMap { clazz -> clazz.getContributesTo(typeResolver) }

    val contributedModulesInDependencies = session.anvilFirDependencyHintProvider
      .getContributions()

    val generated = contributesBindingSymbols.flatMap { symbol ->
      getContributesBindingContributions(
        matchedSymbol = symbol,
        moduleClassId = symbol.classId.bindingModuleSibling,
        typeResolver = typeResolver,
      )
    }

    return contributedModulesInDependencies + fromSession + generated
  }

  private fun getContributesBindingContributions(
    matchedSymbol: FirRegularClassSymbol,
    moduleClassId: ClassId,
    typeResolver: FirSupertypeGenerationExtension.TypeResolveService,
  ): List<ScopedContribution> = matchedSymbol.getContributesBindingAnnotations(this.session)
    .flatMap { annotation ->

      val boundType = lazyWithContext<ClassId, FirSupertypeGenerationExtension.TypeResolveService> {
        annotation.boundTypeArgumentOrNull(session)
          ?.resolveConeType(it)
          ?.requireClassId()
          ?: matchedSymbol.getSuperTypes(
            useSiteSession = session,
            recursive = false,
            lookupInterfaces = true,
          )
            .single()
            .requireClassId()
      }

      val scopeType = annotation.requireScopeArgument(typeResolver).requireClassId()

      listOf(
        ContributedBinding(
          scopeType = cachesFactory.createLazyValue { scopeType },
          boundType = boundType,
          contributedType = matchedSymbol.classId,
          replaces = cachesFactory.createLazyValue { annotation.replacesClassIds() },
          rank = annotation.rankArgumentOrNull(session) ?: ContributesBinding.RANK_NORMAL,
          ignoreQualifier = false,
          isMultibinding = false,
          bindingModule = matchedSymbol.classId.bindingModuleSibling,
          qualifier = null,
        ),
        ContributedModule(
          scopeType = cachesFactory.createLazyValue { scopeType },
          contributedType = moduleClassId,
          replaces = cachesFactory.createLazyValue { annotation.replacesClassIds() },
        ),
      )
    }

  private fun FirRegularClassSymbol.getContributesTo(
    typeResolver: FirSupertypeGenerationExtension.TypeResolveService,
  ): List<ContributedTo> {
    val predicateMatcher = session.predicateBasedProvider
    val clazz = this@getContributesTo

    return contributesToAnnotations(session).map { annotation ->

      val scopeType = cachesFactory.createLazyValue {
        annotation.requireScopeArgument(typeResolver).requireClassId()
      }

      if (predicateMatcher.matches(AnvilPredicates.hasDaggerModule, clazz)) {
        ContributedModule(
          scopeType = scopeType,
          contributedType = classId,
          replaces = cachesFactory.createLazyValue { annotation.replacesClassIds() },
        )
      } else {
        ContributedSupertype(
          scopeType = scopeType,
          contributedType = classId,
          replaces = cachesFactory.createLazyValue { annotation.replacesClassIds() },
        )
      }
    }
  }

  private fun FirAnnotationCall.replacesClassIds() = replacesArgumentOrNull(session)
    ?.map { it.requireTargetClassId() }
    .orEmpty()
}
