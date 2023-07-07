package com.squareup.anvil.compiler.codegen.ksp

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.squareup.anvil.compiler.api.AnvilContext
import com.squareup.anvil.compiler.codegen.toAnvilContext

abstract class AnvilSymbolProcessorProvider(
  private val delegate: (SymbolProcessorEnvironment, AnvilContext) -> SymbolProcessor
) : SymbolProcessorProvider {
  final override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
    val context = environment.toAnvilContext()
    return delegate(environment, context)
  }
}
