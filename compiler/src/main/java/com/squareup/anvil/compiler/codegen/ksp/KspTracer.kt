package com.squareup.anvil.compiler.codegen.ksp

import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.squareup.anvil.compiler.OPTION_VERBOSE
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.time.measureTimedValue

internal interface KspTracer {
  fun log(message: String)
}

internal class KspTracerImpl(
  env: SymbolProcessorEnvironment,
  private val logTag: String,
) : KspTracer {
  private val logger = env.logger
  private val verbose = env.options[OPTION_VERBOSE]?.toBoolean() ?: false

  override fun log(message: String) {
    val messageWithTag = "[Anvil] [$logTag] $message"
    if (verbose) {
      logger.warn(messageWithTag)
    } else {
      logger.info(messageWithTag)
    }
  }
}

@OptIn(ExperimentalContracts::class)
internal inline fun <T> KspTracer.trace(message: String, block: () -> T): T {
  contract { callsInPlace(block, kotlin.contracts.InvocationKind.EXACTLY_ONCE) }
  val (result, duration) = measureTimedValue(block)
  val durationMs = duration.inWholeMilliseconds
  val prefix = if (durationMs > 200) {
    "[SLOW] "
  } else {
    ""
  }
  log("$prefix$message took ${durationMs}ms")
  return result
}
