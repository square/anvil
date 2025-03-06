package com.squareup.anvil.compiler.k2.fir.merging

import com.google.auto.service.AutoService
import com.squareup.anvil.compiler.k2.fir.AnvilFirContext
import com.squareup.anvil.compiler.k2.fir.AnvilFirExtensionFactory
import com.squareup.anvil.compiler.k2.fir.AnvilFirSupertypeGenerationExtension
import com.squareup.anvil.compiler.k2.fir.contributions.AnvilFirScopedContributionProvider
import com.squareup.anvil.compiler.k2.fir.contributions.anvilFirScopedContributionProvider
import com.squareup.anvil.compiler.k2.utils.fir.AnvilPredicates
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirClassLikeDeclaration
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.FirSupertypeGenerationExtension
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef

/**
 * This extension exists in order to ensure that a
 * [FirSupertypeGenerationExtension.TypeResolveService] is provided to
 * [AnvilFirScopedContributionProvider], so that annotation parameter expressions
 * (like `MyScope::class`) can be evaluated inside a `FirDeclarationGenerationExtension`.
 */
public class AnvilContributionResolveExtension(
  anvilFirContext: AnvilFirContext,
  session: FirSession,
) : AnvilFirSupertypeGenerationExtension(anvilFirContext, session) {

  override fun computeAdditionalSupertypes(
    classLikeDeclaration: FirClassLikeDeclaration,
    resolvedSupertypes: List<FirResolvedTypeRef>,
    typeResolver: TypeResolveService,
  ): List<ConeKotlinType> {
    session.anvilFirScopedContributionProvider.getContributions(typeResolver)
    return emptyList()
  }

  override fun needTransformSupertypes(declaration: FirClassLikeDeclaration): Boolean {
    return !session.anvilFirScopedContributionProvider.initialized
  }

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    register(AnvilPredicates.hasAnyAnvilContributes)
  }

  @AutoService(AnvilFirExtensionFactory::class)
  public class Factory : AnvilFirSupertypeGenerationExtension.Factory {
    override fun create(
      anvilFirContext: AnvilFirContext,
    ): FirSupertypeGenerationExtension.Factory {
      return FirSupertypeGenerationExtension.Factory { session ->
        AnvilContributionResolveExtension(anvilFirContext, session)
      }
    }
  }
}
