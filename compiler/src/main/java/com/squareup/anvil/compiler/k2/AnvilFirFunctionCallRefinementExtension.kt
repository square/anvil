package com.squareup.anvil.compiler.k2

import com.squareup.anvil.compiler.k2.internal.pretty
import org.jetbrains.kotlin.analysis.utils.relfection.renderAsDataClassToString
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.extensions.FirExtensionApiInternals
import org.jetbrains.kotlin.fir.extensions.FirFunctionCallRefinementExtension
import org.jetbrains.kotlin.fir.resolve.calls.candidate.CallInfo
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol

@OptIn(FirExtensionApiInternals::class)
public class AnvilFirFunctionCallRefinementExtension(session: FirSession) :
  FirFunctionCallRefinementExtension(session) {

  override fun intercept(callInfo: CallInfo, symbol: FirNamedFunctionSymbol): CallReturnType? {

    val containing = callInfo.containingDeclarations.last()

    error(
      """
      |~~~~~~~~~~~~~~~~~~~~~ intercept
      | -- callInfo
      |${callInfo.renderAsDataClassToString().pretty()}
      |
      | -- containing declaration
      |${containing.renderAsDataClassToString().pretty()}
      |
      | -- symbol
      |${symbol.renderAsDataClassToString().pretty()}
      |~~~~~~~~~~~~~~~~~~~~~
      """.trimMargin(),
    )
  }

  override fun transform(
    call: FirFunctionCall,
    originalSymbol: FirNamedFunctionSymbol,
  ): FirFunctionCall {

    error(
      """
      |~~~~~~~~~~~~~~~~~~~~~ transform
      |            call -- $call
      | original symbol -- ${originalSymbol.name}
      |~~~~~~~~~~~~~~~~~~~~~
      """.trimMargin(),
    )
  }
}
