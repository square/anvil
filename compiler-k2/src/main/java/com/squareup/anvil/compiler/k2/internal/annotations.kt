package com.squareup.anvil.compiler.k2.internal

import com.squareup.anvil.compiler.api.AnvilCompilationException
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.expressions.FirArrayLiteral
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirGetClassCall
import org.jetbrains.kotlin.fir.expressions.FirNamedArgumentExpression
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.name.Name

internal fun FirAnnotation.getGetKClassArgument(name: Name): FirGetClassCall? {
  return argumentAt(name, 0) as? FirGetClassCall
}

internal fun FirAnnotationCall.requireScopeArgument(): FirGetClassCall {
  return requireArgumentAt(name = Name.identifier("scope"), index = 0) as FirGetClassCall
}

// TODO have Joel rename this
internal fun FirAnnotationCall.classListArgumentAt(name: Name, index: Int): List<FirGetClassCall>? {
  val arrayArg = argumentAt(name = name, index = index) as? FirArrayLiteral
    ?: return null

  return arrayArg.arguments.map { it as FirGetClassCall }
}

internal fun FirAnnotationCall.requireArgumentAt(
  name: String,
  index: Int,
): FirExpression = requireArgumentAt(name = Name.identifier(name), index = index)

internal fun FirAnnotationCall.requireArgumentAt(
  name: Name,
  index: Int,
): FirExpression = argumentAt(name = name, index = index)
  ?: throw AnvilCompilationException(
    """
      |Could not find required argument for annotation: $this
      |                      Required name: $name
      |Required index (if no name is used): $index
      |existing arguments:
      |${arguments.joinToString("\n") { "    $it" }}
    """.trimMargin(),
  )

internal fun FirAnnotation.argumentAt(
  name: String,
  index: Int,
): FirExpression? = argumentAt(name = Name.identifier(name), index = index)

internal fun FirAnnotation.argumentAt(
  name: Name,
  index: Int,
): FirExpression? {
  argumentMapping.mapping[name]?.let { return it }

  if (this !is FirAnnotationCall) return null

  var nameUsed = false
  // NB: we have to consider both cases, because deserializer does not create argument mapping
  for ((i, argument) in arguments.withIndex()) {
    if (argument is FirNamedArgumentExpression) {
      nameUsed = true
      if (argument.name == name) {
        return argument.expression
      }
    } else if (!nameUsed && i == index) {
      return argument
    }
  }

  return null
}
