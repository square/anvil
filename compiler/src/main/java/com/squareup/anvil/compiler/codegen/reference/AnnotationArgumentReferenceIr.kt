package com.squareup.anvil.compiler.codegen.reference

import com.squareup.anvil.compiler.kclassUnwrapped
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import kotlin.LazyThreadSafetyMode.NONE

internal class AnnotationArgumentReferenceIr(
  val argumentParameter: IrValueParameter,
  val argumentExpression: IrExpression,
  val annotation: AnnotationReferenceIr,
) {
  val name: String = argumentParameter.name.toString()

  val context: IrPluginContext
    get() = annotation.context

  private val value: Any by lazy(NONE) {
    findValue()
  }

  // We currently special-case for Classes, but this is the spot we'll need to update if we need to
  // support primitives later on.
  @OptIn(UnsafeDuringIrConstructionAPI::class)
  private fun findValue(): Any {
    (argumentExpression as? IrConst)?.let {
      return it.value as Any
    }

    (argumentExpression as? IrVararg)?.elements?.let { element ->
      return element.filterIsInstance<IrExpression>()
        .map { it.kclassUnwrapped.owner as IrClass }
        .map { it.symbol.toClassReference(context) }
    }

    (argumentExpression.kclassUnwrapped.owner as? IrClass)?.let {
      return it.symbol.toClassReference(context)
    }

    return argumentExpression
  }

  // Maybe we need to make the return type nullable and allow for a default value.
  @Suppress("UNCHECKED_CAST")
  fun <T : Any> value(): T = value as T

  override fun toString(): String {
    return "${AnnotationArgumentReferenceIr::class.simpleName}(name=$name, value=$value)"
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is AnnotationArgumentReferenceIr) return false

    if (name != other.name) return false
    if (value != other.value) return false

    return true
  }

  override fun hashCode(): Int {
    var result = name.hashCode()
    result = 31 * result + value.hashCode()
    return result
  }
}

internal fun Pair<IrValueParameter, IrExpression>.toAnnotationArgumentReference(
  annotation: AnnotationReferenceIr,
): AnnotationArgumentReferenceIr {
  return AnnotationArgumentReferenceIr(
    argumentParameter = this.first,
    argumentExpression = this.second,
    annotation = annotation,
  )
}
