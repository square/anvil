package com.squareup.anvil.compiler.k2.fir.merging

import com.google.auto.service.AutoService
import com.squareup.anvil.compiler.k2.fir.AnvilFirContext
import com.squareup.anvil.compiler.k2.fir.AnvilFirExtensionFactory
import com.squareup.anvil.compiler.k2.fir.AnvilFirSupertypeGenerationExtension
import com.squareup.anvil.compiler.k2.fir.internal.AnvilPredicates
import com.squareup.anvil.compiler.k2.fir.internal.Names
import com.squareup.anvil.compiler.k2.fir.internal.contributesToScope
import com.squareup.anvil.compiler.k2.fir.internal.hasAnnotation
import com.squareup.anvil.compiler.k2.fir.internal.mapToSet
import com.squareup.anvil.compiler.k2.fir.internal.requireScopeArgument
import com.squareup.anvil.compiler.k2.fir.internal.resolveConeType
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

        println(
          """
        |############################
        |${
            session.contributedThingsProvider
              .getContributedThingsForScope(scopeFqName)
              .joinToString("\n")
          }
        |############################
          """.trimMargin(),
        )

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
    return session.predicateBasedProvider.matches(AnvilPredicates.hasMergeComponentAnnotation, declaration)
  }
}
