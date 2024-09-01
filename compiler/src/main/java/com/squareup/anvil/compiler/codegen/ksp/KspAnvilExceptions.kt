package com.squareup.anvil.compiler.codegen.ksp

import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSNode

internal class KspAnvilException(
  override val message: String,
  val node: KSNode,
  override val cause: Throwable? = null,
) : Exception()

/**
 * In rare cases, we might encounter error types that we can't easily forward back up the call
 * stack. With great shame, I hang my head in defeat with this type.
 */
internal class KspErrorTypeException(val typesToDefer: List<KSAnnotated>) : Exception() {
  constructor(vararg types: KSAnnotated) : this(types.toList())
}
