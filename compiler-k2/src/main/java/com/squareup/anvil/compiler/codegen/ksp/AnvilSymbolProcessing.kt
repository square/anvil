package com.squareup.anvil.compiler.codegen.ksp

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.squareup.anvil.compiler.api.AnvilApplicabilityChecker
import com.squareup.anvil.compiler.codegen.toAnvilContext

private object NoOpProcessor : SymbolProcessor {
  override fun process(resolver: Resolver): List<KSAnnotated> = emptyList()
}

internal open class AnvilSymbolProcessorProvider(
  private val applicabilityChecker: AnvilApplicabilityChecker,
  private val delegate: (SymbolProcessorEnvironment) -> AnvilSymbolProcessor,
) : SymbolProcessorProvider {
  final override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
    val context = environment.toAnvilContext()
    if (!applicabilityChecker.isApplicable(context)) return NoOpProcessor
    return delegate(environment)
  }
}

internal abstract class AnvilSymbolProcessor : SymbolProcessor {
  abstract val env: SymbolProcessorEnvironment

  final override fun process(resolver: Resolver): List<KSAnnotated> {
    return try {
      processChecked(resolver)
    } catch (e: KspAnvilException) {
      env.logger.error(e.message, e.node)
      e.cause?.let(env.logger::exception)
      emptyList()
    }
  }

  protected abstract fun processChecked(resolver: Resolver): List<KSAnnotated>
}
