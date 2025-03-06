package com.squareup.anvil.compiler.k2.fir.merging

import com.google.auto.service.AutoService
import com.squareup.anvil.compiler.k2.fir.AnvilFirContext
import com.squareup.anvil.compiler.k2.fir.AnvilFirExtensionFactory
import com.squareup.anvil.compiler.k2.fir.AnvilFirSupertypeGenerationExtension
import com.squareup.anvil.compiler.k2.utils.fir.AnvilPredicates
import com.squareup.anvil.compiler.k2.utils.fir.contributesToScope
import com.squareup.anvil.compiler.k2.utils.fir.hasAnnotation
import com.squareup.anvil.compiler.k2.utils.fir.requireScopeArgument
import com.squareup.anvil.compiler.k2.utils.fir.resolveConeType
import com.squareup.anvil.compiler.k2.utils.names.ClassIds
import com.squareup.anvil.compiler.k2.utils.stdlib.mapToSet
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirClassLikeDeclaration
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.FirSupertypeGenerationExtension
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.plugin.createConeType
import org.jetbrains.kotlin.fir.resolve.fqName
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.classId

/**
 * This extension finds all contributed component interfaces and adds them as super types to Dagger
 * components annotated with `@MergeComponent`
 */
public class AnvilFirInterfaceMergingExtension(
  anvilFirContext: AnvilFirContext,
  session: FirSession,
) : AnvilFirSupertypeGenerationExtension(anvilFirContext, session) {

  @AutoService(AnvilFirExtensionFactory::class)
  public class Factory : AnvilFirSupertypeGenerationExtension.Factory {
    override fun create(anvilFirContext: AnvilFirContext): FirSupertypeGenerationExtension.Factory {
      return FirSupertypeGenerationExtension.Factory { session ->
        AnvilFirInterfaceMergingExtension(anvilFirContext, session)
      }
    }
  }

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    register(AnvilPredicates.hasAnvilContributesTo)
    register(AnvilPredicates.hasModuleAnnotation)
  }

  override fun computeAdditionalSupertypes(
    classLikeDeclaration: FirClassLikeDeclaration,
    resolvedSupertypes: List<FirResolvedTypeRef>,
    typeResolver: TypeResolveService,
  ): List<ConeKotlinType> {

    val existingSupertypes = resolvedSupertypes.mapToSet { it.coneType.classId }

    return classLikeDeclaration.annotations
      .filter { it.fqName(session) == ClassIds.anvilMergeComponent.asSingleFqName() }
      .flatMap { annotation ->

        val scopeFqName = (annotation as FirAnnotationCall).requireScopeArgument()
          .resolveConeType(typeResolver)
          .classId!!
          .asSingleFqName()

        session.predicateBasedProvider
          .getSymbolsByPredicate(AnvilPredicates.hasAnvilContributesTo)
          .filterIsInstance<FirClassLikeSymbol<*>>()
          .mapNotNull { contributed ->

            val classId = contributed.classId

            // If the class is already a supertype, we can't add it again.
            if (classId in existingSupertypes) return@mapNotNull null

            // If it's a contributed module, we don't add it here
            if (contributed.hasAnnotation(ClassIds.daggerModule, session)) {
              return@mapNotNull null
            }

            // Only merge contributions to this scope
            if (!contributed.contributesToScope(
                scopeFqName,
                session,
                typeResolver,
              )
            ) {
              return@mapNotNull null
            }

            classId.createConeType(session)
          }
      }
  }

  override fun needTransformSupertypes(declaration: FirClassLikeDeclaration): Boolean {
    return session.predicateBasedProvider.matches(
      AnvilPredicates.hasMergeComponentAnnotation,
      declaration,
    )
  }
}
