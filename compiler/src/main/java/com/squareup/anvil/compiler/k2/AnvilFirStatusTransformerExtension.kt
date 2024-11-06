package com.squareup.anvil.compiler.k2

import org.jetbrains.kotlin.analysis.utils.relfection.renderAsDataClassToString
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirDeclarationStatus
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.extensions.FirStatusTransformerExtension
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol

public class AnvilFirStatusTransformerExtension(session: FirSession) : FirStatusTransformerExtension(
  session,
) {
  override fun needTransformStatus(declaration: FirDeclaration): Boolean {
    return false
  }

  override fun transformStatus(
    status: FirDeclarationStatus,
    regularClass: FirRegularClass,
    containingClass: FirClassLikeSymbol<*>?,
    isLocal: Boolean,
  ): FirDeclarationStatus {

    error(
      """
      |=================================================================
      |         status -- ${status.renderAsDataClassToString()}
      |   regularClass -- ${regularClass.name}
      |containingClass -- ${containingClass?.name}
      |
      | -- text
      |${regularClass.source?.getElementTextInContextForDebug()}
      |=================================================================
      """.trimMargin(),
    )
    return super.transformStatus(status, regularClass, containingClass, isLocal)
  }
}
