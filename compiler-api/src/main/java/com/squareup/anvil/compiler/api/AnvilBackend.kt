package com.squareup.anvil.compiler.api

/** Possible modes of Anvil compilation. */
public enum class AnvilBackend {
  /** Anvil runs as a direct compiler plugin inside compileKotlin tasks. */
  EMBEDDED,

  /** Anvil runs as a Kotlin Symbol Processor (KSP). */
  KSP,
}
