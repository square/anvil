package com.squareup.anvil.compiler.k2

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.expressions.FirStatement
import org.jetbrains.kotlin.fir.expressions.FirVariableAssignment
import org.jetbrains.kotlin.fir.extensions.FirAssignExpressionAltererExtension
import org.jetbrains.kotlin.text

public class AnvilFirAssignExpressionAltererExtension(session: FirSession) :
  FirAssignExpressionAltererExtension(session) {

  override fun transformVariableAssignment(
    variableAssignment: FirVariableAssignment,
  ): FirStatement? {
    error("variableAssignment -- ${variableAssignment.source?.text}")
  }
}
