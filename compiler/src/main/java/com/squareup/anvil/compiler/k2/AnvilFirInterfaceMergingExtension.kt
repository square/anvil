package com.squareup.anvil.compiler.k2

import com.squareup.anvil.compiler.k2.internal.Names
import com.squareup.anvil.compiler.k2.internal.classId
import com.squareup.anvil.compiler.k2.internal.createUserType
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirClassLikeDeclaration
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.FirSupertypeGenerationExtension
import org.jetbrains.kotlin.fir.extensions.predicate.DeclarationPredicate
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneType

public class AnvilFirInterfaceMergingExtension(session: FirSession) :
  FirSupertypeGenerationExtension(session) {

  private companion object {
    private val annotationClassId = Names.mergeComponentFir.classId()
    private val PREDICATE = DeclarationPredicate.create {
      annotated(annotationClassId.asSingleFqName())
    }
  }

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    register(PREDICATE)
  }

  override fun computeAdditionalSupertypes(
    classLikeDeclaration: FirClassLikeDeclaration,
    resolvedSupertypes: List<FirResolvedTypeRef>,
    typeResolver: TypeResolveService,
  ): List<FirResolvedTypeRef> {

    val supertypeUserType = Names.componentBase.createUserType(sourceElement = null)

    val alreadyHasComponentBase = resolvedSupertypes.any {
      it.coneType.classId?.asFqNameString() == Names.componentBase.asString()
    }
    if (alreadyHasComponentBase) return emptyList()

    val superResolved = typeResolver.resolveUserType(supertypeUserType)

    check(!resolvedSupertypes.contains(superResolved)) {
      "Supertype $supertypeUserType is already present in $resolvedSupertypes"
    }

    return listOf(superResolved)
  }

  override fun needTransformSupertypes(declaration: FirClassLikeDeclaration): Boolean {
    return session.predicateBasedProvider.matches(PREDICATE, declaration)
  }
}
