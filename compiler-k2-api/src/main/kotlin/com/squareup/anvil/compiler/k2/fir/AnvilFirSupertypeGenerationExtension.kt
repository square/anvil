package com.squareup.anvil.compiler.k2.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirClassLikeDeclaration
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.extensions.ExperimentalSupertypesGenerationApi
import org.jetbrains.kotlin.fir.extensions.FirSupertypeGenerationExtension
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef

public abstract class AnvilFirSupertypeGenerationExtension(
  override val anvilFirContext: AnvilFirContext,
  session: FirSession,
) : FirSupertypeGenerationExtension(session),
  AnvilFirExtension {
  public fun interface Factory : AnvilFirExtensionFactory<FirSupertypeGenerationExtension.Factory>
}

private class Foo(
  anvilFirContext: AnvilFirContext,
  session: FirSession,
) : AnvilFirSupertypeGenerationExtension(anvilFirContext, session) {
  override fun needTransformSupertypes(declaration: FirClassLikeDeclaration): Boolean {
    TODO("Not yet implemented")
  }

  override fun computeAdditionalSupertypes(
    classLikeDeclaration: FirClassLikeDeclaration,
    resolvedSupertypes: List<FirResolvedTypeRef>,
    typeResolver: TypeResolveService,
  ): List<ConeKotlinType> {
    TODO("Not yet implemented")
  }

  @ExperimentalSupertypesGenerationApi
  override fun computeAdditionalSupertypesForGeneratedNestedClass(
    klass: FirRegularClass,
    typeResolver: TypeResolveService,
  ): List<FirResolvedTypeRef> {
    return super.computeAdditionalSupertypesForGeneratedNestedClass(klass, typeResolver)
  }
}
