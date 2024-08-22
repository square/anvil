package com.squareup.anvil.compiler.codegen.ksp

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.squareup.anvil.compiler.api.AnvilApplicabilityChecker
import com.squareup.anvil.compiler.codegen.toAnvilContext
import com.squareup.kotlinpoet.asClassName
import kotlin.time.measureTimedValue

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

internal abstract class AnvilSymbolProcessor : SymbolProcessor, KspTracer {
  abstract val env: SymbolProcessorEnvironment
  private val logTag = this::class.asClassName().simpleNames.joinToString(".")
  private var round = 0
  private var totalTime = 0L

  private val tracer by lazy(LazyThreadSafetyMode.NONE) {
    KspTracerImpl(env, logTag)
  }

  final override fun log(message: String) {
    tracer.log(message)
  }

  final override fun process(resolver: Resolver): List<KSAnnotated> {
    round++
    log("Starting round $round")
    val (result, duration) = measureTimedValue {
      runInternal(resolver)
    }
    val durationMs = duration.inWholeMilliseconds
    totalTime += durationMs
    log("Round $round took ${durationMs}ms")
    return result
  }

  private fun runInternal(resolver: Resolver): List<KSAnnotated> {
    return try {
      processChecked(resolver)
    } catch (e: KspAnvilException) {
      env.logger.error(e.message, e.node)
      e.cause?.let(env.logger::exception)
      emptyList()
    }
  }

  override fun finish() {
    log("Total processing time after $round round(s) took ${totalTime}ms")
  }

  protected abstract fun processChecked(resolver: Resolver): List<KSAnnotated>
}
