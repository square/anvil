package com.squareup.anvil.compiler.k2

import com.squareup.anvil.compiler.k2.internal.AnvilPredicates
import com.squareup.anvil.compiler.k2.internal.Names
import com.squareup.anvil.compiler.k2.internal.contributesToScope
import com.squareup.anvil.compiler.k2.internal.hasAnnotation
import com.squareup.anvil.compiler.k2.internal.mapToSet
import com.squareup.anvil.compiler.k2.internal.requireScopeArgument
import com.squareup.anvil.compiler.k2.internal.resolveConeType
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
 * Add
 */
public class AnvilFirInterfaceMergingExtension(session: FirSession) :
  FirSupertypeGenerationExtension(session) {

  private companion object {
    private val PREDICATE = AnvilPredicates.hasMergeComponentFirAnnotation
  }

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    register(PREDICATE)
    register(AnvilPredicates.hasContributesToAnnotation)
    register(AnvilPredicates.hasModuleAnnotation)
  }

  override fun computeAdditionalSupertypes(
    classLikeDeclaration: FirClassLikeDeclaration,
    resolvedSupertypes: List<FirResolvedTypeRef>,
    typeResolver: TypeResolveService,
  ): List<ConeKotlinType> {

    val existingSupertypes = resolvedSupertypes.mapToSet { it.coneType.classId }

    return classLikeDeclaration.annotations
      .filter { it.fqName(session) == Names.anvil.mergeComponent }
      .flatMap { annotation ->

        val scopeFqName = (annotation as FirAnnotationCall).requireScopeArgument()
          .resolveConeType(typeResolver)
          .classId!!
          .asSingleFqName()

        session.predicateBasedProvider
          .getSymbolsByPredicate(AnvilPredicates.hasContributesToAnnotation)
          .filterIsInstance<FirClassLikeSymbol<*>>()
          .mapNotNull { contributed ->

            val classId = contributed.classId

            // If the class is already a supertype, we can't add it again.
            if (classId in existingSupertypes) return@mapNotNull null

            // If it's a contributed module, we don't add it here
            if (contributed.hasAnnotation(Names.dagger.module, session)) return@mapNotNull null

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
    return session.predicateBasedProvider.matches(PREDICATE, declaration)
  }
}
