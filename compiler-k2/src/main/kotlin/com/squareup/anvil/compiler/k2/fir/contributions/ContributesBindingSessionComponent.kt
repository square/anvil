package com.squareup.anvil.compiler.k2.fir.contributions

import com.squareup.anvil.compiler.k2.fir.AnvilFirContext
import com.squareup.anvil.compiler.k2.fir.AnvilFirExtensionSessionComponent
import com.squareup.anvil.compiler.k2.util.AnvilPredicates.hasContributesBindingAnnotation
import com.squareup.anvil.compiler.k2.utils.names.bindingModuleSibling
import com.squareup.anvil.compiler.k2.utils.fir.AnvilPredicates.hasAnvilContributesBinding
import com.squareup.anvil.compiler.k2.utils.names.joinSimpleNames
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.caches.FirCache
import org.jetbrains.kotlin.fir.caches.firCachesFactory
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.name.ClassId

/**
 * Responsible for tracking the classes annotated with @ContributesBinding and creating + caching
 * their generated Dagger module metadata.
 */
public class ContributesBindingSessionComponent(
  anvilFirContext: AnvilFirContext,
  session: FirSession,
) : AnvilFirExtensionSessionComponent(anvilFirContext, session) {
  /**
   * A map to help us track the original annotated classes' bindings, and their
   * generated module IDs.
   * E.g. Key: "Foo_BindingModule", Value: ClassSymbol<Foo>
   */
  public val generatedIdsToMatchedSymbols: Map<ClassId, FirRegularClassSymbol> by lazy {
    session.predicateBasedProvider.getSymbolsByPredicate(hasAnvilContributesBinding)
      .filterIsInstance<FirRegularClassSymbol>()
      .associateBy {
        it.classId.bindingModuleSibling
      }
  }

  public val bindingModuleCache: FirCache<ClassId, BindingModuleData, FirSession> =
    session.firCachesFactory
      .createCache<ClassId, BindingModuleData, FirSession> { key: ClassId, context ->
        BindingModuleData(
          generatedClassId = key,
          matchedClassSymbol = generatedIdsToMatchedSymbols.getValue(key),
          firExtension = this@ContributesBindingSessionComponent,
          session = session,
        )
      }

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    register(hasAnvilContributesBinding)
  }
}

public val FirSession.contributesBindingSessionComponent: ContributesBindingSessionComponent
  by FirSession.sessionComponentAccessor()
