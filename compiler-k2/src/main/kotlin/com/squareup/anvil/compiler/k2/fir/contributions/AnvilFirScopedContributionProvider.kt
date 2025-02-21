package com.squareup.anvil.compiler.k2.fir.contributions

import com.google.auto.service.AutoService
import com.squareup.anvil.compiler.k2.fir.AnvilFirContext
import com.squareup.anvil.compiler.k2.fir.AnvilFirExtensionFactory
import com.squareup.anvil.compiler.k2.fir.AnvilFirExtensionSessionComponent
import com.squareup.anvil.compiler.k2.fir.internal.AnvilPredicates
import com.squareup.anvil.compiler.k2.fir.internal.contributesToAnnotations
import com.squareup.anvil.compiler.k2.fir.internal.requireClassId
import com.squareup.anvil.compiler.k2.fir.internal.requireReplacesArgument
import com.squareup.anvil.compiler.k2.fir.internal.requireScopeArgument
import com.squareup.anvil.compiler.k2.utils.names.ClassIds
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.caches.FirCache
import org.jetbrains.kotlin.fir.caches.firCachesFactory
import org.jetbrains.kotlin.fir.caches.getValue
import org.jetbrains.kotlin.fir.declarations.getTargetType
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.FirExtensionSessionComponent
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.name.ClassId

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

  private var map: Map<ClassId, List<ContributedTo>>? = null

  private val contributesTo: FirCache<ClassId, List<ContributedTo>, Nothing?> =
    session.firCachesFactory.createCache { scopeType, _ ->
      createContributedThings(scopeType)
    }

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    register(AnvilPredicates.contributedModule)
    register(AnvilPredicates.hasContributesToAnnotation)
  }

  public fun getContributedThingsForScope(
    scopeType: ClassId,
  ): List<ContributedTo> = contributesTo.getValue(scopeType)

  private fun initMap(): Map<ClassId, List<ContributedTo>> {

    return session.predicateBasedProvider
      .getSymbolsByPredicate(AnvilPredicates.hasContributesToAnnotation)
      .filterIsInstance<FirClassLikeSymbol<*>>()
      .flatMap { clazz ->
        clazz.contributesToAnnotations(session)
          .map { annotation ->

            val scopeType = annotation.requireScopeArgument(session).requireClassId()

            if (clazz.hasAnnotation(ClassIds.daggerModule, session)) {
              ContributedModule(
                scopeType = scopeType,
                contributedType = clazz.classId,
                replaces = annotation.requireReplacesArgument(session)
                  .map {
                    requireNotNull(it.getTargetType()) { "Replaces argument must be a type" }
                      .requireClassId()
                  },
              )
            } else {
              ContributedSupertype(
                scopeType = scopeType,
                contributedType = clazz.classId,
                replaces = annotation.requireReplacesArgument(session)
                  .map {
                    requireNotNull(it.getTargetType()) { "Replaces argument must be a type" }
                      .requireClassId()
                  },
              )
            }
          }
      }
      .groupBy { it.scopeType }
      .also { map = it }
  }

  private fun createContributedThings(scopeType: ClassId): List<ContributedTo> {

    val m = map ?: initMap()

    return m[scopeType] ?: emptyList()
  }
}

public val FirSession.anvilFirScopedContributionProvider: AnvilFirScopedContributionProvider
  by FirSession.sessionComponentAccessor()
