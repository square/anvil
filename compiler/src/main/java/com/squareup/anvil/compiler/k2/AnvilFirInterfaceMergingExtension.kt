package com.squareup.anvil.compiler.k2

import com.squareup.anvil.compiler.k2.internal.Names.anvil
import com.squareup.anvil.compiler.k2.internal.Names.foo
import com.squareup.anvil.compiler.k2.internal.classId
import com.squareup.anvil.compiler.k2.internal.createUserType
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirClassLikeDeclaration
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.FirSupertypeGenerationExtension
import org.jetbrains.kotlin.fir.extensions.predicate.DeclarationPredicate
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.classId

public class AnvilFirInterfaceMergingExtension(session: FirSession) :
  FirSupertypeGenerationExtension(session) {

  private companion object {
    private val annotationClassId = anvil.mergeComponent.classId()
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
  ): List<ConeKotlinType> {

    val supertypeUserType = foo.componentBase.createUserType(sourceElement = null)

    val alreadyHasComponentBase = resolvedSupertypes.any {
      it.coneType.classId?.asFqNameString() == foo.componentBase.asString()
    }
    if (alreadyHasComponentBase) return emptyList()

    val superResolved = typeResolver.resolveUserType(supertypeUserType)

    check(!resolvedSupertypes.contains(superResolved)) {
      "Supertype $supertypeUserType is already present in $resolvedSupertypes"
    }

    return listOf(superResolved.coneType)
  }

  override fun needTransformSupertypes(declaration: FirClassLikeDeclaration): Boolean {
    return session.predicateBasedProvider.matches(PREDICATE, declaration)
  }
}
