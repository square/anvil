package com.squareup.anvil.compiler.k2.fir.abstraction.providers

import com.google.auto.service.AutoService
import com.squareup.anvil.compiler.k2.fir.AnvilFirContext
import com.squareup.anvil.compiler.k2.fir.AnvilFirExtensionFactory
import com.squareup.anvil.compiler.k2.fir.AnvilFirExtensionSessionComponent
import com.squareup.anvil.compiler.k2.utils.fir.AnvilPredicates
import com.squareup.anvil.compiler.k2.utils.fir.hasAnnotation
import com.squareup.anvil.compiler.k2.utils.names.ClassIds
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.caches.getValue
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.FirExtensionSessionComponent
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol

public val FirSession.anvilFirSymbolProvider: AnvilFirSymbolProvider by FirSession.sessionComponentAccessor()

public class AnvilFirSymbolProvider(
  anvilFirContext: AnvilFirContext,
  session: FirSession,
) : AnvilFirExtensionSessionComponent(anvilFirContext, session) {

  /*
  Anvil Contributes annotations
   */
  internal val contributesBindingSymbols: List<FirRegularClassSymbol>
    by lazySymbols<FirRegularClassSymbol>(AnvilPredicates.hasAnvilContributesBinding)
  internal val contributesMultibindingSymbols: List<FirRegularClassSymbol>
    by lazySymbols<FirRegularClassSymbol>(AnvilPredicates.hasAnvilContributesMultibinding)
  internal val contributesSubcomponentSymbols: List<FirRegularClassSymbol>
    by lazySymbols<FirRegularClassSymbol>(AnvilPredicates.hasAnvilContributesSubcomponent)
  internal val contributesToSymbols: List<FirRegularClassSymbol>
    by lazySymbols<FirRegularClassSymbol>(AnvilPredicates.hasAnvilContributesTo)
  internal val contributesSupertypeSymbols: List<FirRegularClassSymbol>
    by lazySymbols<FirRegularClassSymbol>(AnvilPredicates.hasAnvilContributesTo)
      .map { symbols ->
        symbols.filter { !it.hasAnnotation(session, ClassIds.daggerModule) }
      }
  internal val contributesModulesSymbols: List<FirRegularClassSymbol>
    by lazySymbols<FirRegularClassSymbol>(AnvilPredicates.contributedModule)
  internal val internalContributedModuleHintsSymbols: List<FirRegularClassSymbol>
    by lazySymbols<FirRegularClassSymbol>(AnvilPredicates.hasAnvilInternalContributedModuleHints)

  /*
  Anvil Merge annotations
   */
  internal val mergeComponentSymbols: List<FirRegularClassSymbol>
    by lazySymbols<FirRegularClassSymbol>(AnvilPredicates.hasAnvilMergeComponent)
  internal val mergeSubcomponentSymbols: List<FirRegularClassSymbol>
    by lazySymbols<FirRegularClassSymbol>(AnvilPredicates.hasAnvilMergeSubcomponent)
  internal val mergeModulesSymbols: List<FirRegularClassSymbol>
    by lazySymbols<FirRegularClassSymbol>(AnvilPredicates.hasAnvilMergeModules)
  internal val mergeInterfacesSymbols: List<FirRegularClassSymbol>
    by lazySymbols<FirRegularClassSymbol>(AnvilPredicates.hasAnvilMergeInterfaces)

  /*
   Dagger annotations
   */
  internal val daggerModuleSymbols: List<FirRegularClassSymbol>
    by lazySymbols<FirRegularClassSymbol>(AnvilPredicates.hasDaggerModule)
  internal val daggerBindsSymbols: List<FirRegularClassSymbol>
    by lazySymbols<FirRegularClassSymbol>(AnvilPredicates.hasDaggerBinds)
  internal val daggerComponentSymbols: List<FirRegularClassSymbol>
    by lazySymbols<FirRegularClassSymbol>(AnvilPredicates.hasDaggerComponent)
  internal val daggerSubcomponentSymbols: List<FirRegularClassSymbol>
    by lazySymbols<FirRegularClassSymbol>(AnvilPredicates.hasDaggerSubcomponent)
  internal val daggerProvidesSymbols: List<FirRegularClassSymbol>
    by lazySymbols<FirRegularClassSymbol>(AnvilPredicates.hasDaggerProvides)

  /*
   JSR-330 annotations
   */
  internal val injectSymbols: List<FirBasedSymbol<*>>
    by lazySymbols<FirRegularClassSymbol>(AnvilPredicates.hasInjectAnnotation)

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    register(AnvilPredicates.hasAnyAnvilContributes)
    register(AnvilPredicates.hasAnyAnvilMerge)
    register(AnvilPredicates.hasAnyDaggerAnnotation)
    register(AnvilPredicates.hasInjectAnnotation)
  }
}

@AutoService(AnvilFirExtensionFactory::class)
public class AnvilFirSymbolProviderFactory : AnvilFirExtensionSessionComponent.Factory {
  override fun create(anvilFirContext: AnvilFirContext): FirExtensionSessionComponent.Factory {
    return FirExtensionSessionComponent.Factory { session ->
      AnvilFirSymbolProvider(anvilFirContext, session)
    }
  }
}
