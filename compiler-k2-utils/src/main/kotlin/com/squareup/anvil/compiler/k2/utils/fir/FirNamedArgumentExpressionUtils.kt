package com.squareup.anvil.compiler.k2.utils.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.expressions.FirNamedArgumentExpression
import org.jetbrains.kotlin.fir.expressions.builder.buildArgumentList
import org.jetbrains.kotlin.fir.expressions.builder.buildArrayLiteral
import org.jetbrains.kotlin.fir.expressions.builder.buildNamedArgumentExpression
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name

public fun createClassListArgument(
  argumentName: Name,
  classes: List<ClassId>,
  session: FirSession,
): FirNamedArgumentExpression = buildNamedArgumentExpression {
  this.name = argumentName
  isSpread = false
  expression = buildArrayLiteral {
    argumentList = buildArgumentList {
      arguments += classes.map { it.requireClassLikeSymbol(session).toGetClassCall() }
    }
  }
}
