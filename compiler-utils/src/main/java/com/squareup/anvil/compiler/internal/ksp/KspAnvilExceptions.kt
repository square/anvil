package com.squareup.anvil.compiler.internal.ksp

import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSNode

public class KspAnvilException(
  override val message: String,
  public val node: KSNode,
  override val cause: Throwable? = null,
) : Exception()

/**
 * In rare cases, we might encounter error types that we can't easily forward back up the call
 * stack. With great shame, I hang my head in defeat with this type.
 */
public class KspErrorTypeException(public val typesToDefer: List<KSAnnotated>) : Exception() {
  public constructor(vararg types: KSAnnotated) : this(types.toList())
}
