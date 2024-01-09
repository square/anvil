package com.squareup.anvil.compiler.codegen.ksp

import com.google.devtools.ksp.symbol.KSNode

internal class KspAnvilException(
  override val message: String,
  val node: KSNode,
  override val cause: Throwable? = null,
) : Exception()
