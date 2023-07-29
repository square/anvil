package com.squareup.anvil.compiler.codegen.ksp

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.squareup.anvil.compiler.api.AnvilContext
import com.squareup.anvil.compiler.codegen.toAnvilContext

internal abstract class AnvilSymbolProcessorProvider(
  private val delegate: (SymbolProcessorEnvironment, AnvilContext) -> AnvilSymbolProcessor
) : SymbolProcessorProvider {
  final override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
    val context = environment.toAnvilContext()
    return delegate(environment, context)
  }
}

internal abstract class AnvilSymbolProcessor(
  protected val env: SymbolProcessorEnvironment,
  private val context: AnvilContext,
) : SymbolProcessor {

  final override fun process(resolver: Resolver): List<KSAnnotated> {
    if (!isApplicable(context)) return emptyList()
    return try {
      processChecked(resolver)
    } catch (e: KspAnvilException) {
      env.logger.error(e.message, e.node)
      e.cause?.let(env.logger::exception)
      emptyList()
    }
  }

  protected abstract fun isApplicable(context: AnvilContext): Boolean

  protected abstract fun processChecked(resolver: Resolver): List<KSAnnotated>
}
