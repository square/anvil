package com.squareup.anvil.compiler.api

/**
 * Possible modes of Anvil analysis during compilation This includes factory generation and hint
 * generation.
 */
public enum class AnalysisBackend {
  /** Anvil runs as a direct compiler plugin inside compileKotlin tasks. */
  EMBEDDED,

  /** Anvil runs as a Kotlin Symbol Processor (KSP). */
  KSP,
}
