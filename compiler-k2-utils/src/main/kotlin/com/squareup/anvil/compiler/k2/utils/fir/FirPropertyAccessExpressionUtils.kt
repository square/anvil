package com.squareup.anvil.compiler.k2.utils.fir

import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.references.impl.FirSimpleNamedReference
import org.jetbrains.kotlin.name.Name

public fun FirPropertyAccessExpression.qualifierSegmentsWithSelf(): List<Name> = buildList<Name> {
  fun visitQualifiers(expression: FirExpression) {
    if (expression !is FirPropertyAccessExpression) return
    expression.explicitReceiver?.let { visitQualifiers(it) }
    expression.qualifierName?.let { add(it) }
  }
  visitQualifiers(this@qualifierSegmentsWithSelf)
}

public val FirPropertyAccessExpression.qualifierName: Name?
  get() = (calleeReference as? FirSimpleNamedReference)?.name
