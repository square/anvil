package com.squareup.anvil.compiler.k2

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.extensions.FirExpressionResolutionExtension
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.text

public class AnvilFirExpressionResolutionExtension(
  session: FirSession,
) : FirExpressionResolutionExtension(session) {

//  private val predicateBasedProvider = session.predicateBasedProvider
//  private val matchedClasses by lazy {
//    predicateBasedProvider.getSymbolsByPredicate(LookupPredicate.create {  })
//  }

  override fun addNewImplicitReceivers(functionCall: FirFunctionCall): List<ConeKotlinType> {
//    return emptyList()
    error("functionCall -- ${functionCall.source?.text}")
  }
}
