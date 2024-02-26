package com.squareup.anvil.compiler.api

/** Possible modes of component merging. */
public enum class ModuleMergingBackend {
  /** Module merging runs as an IR plugin during kapt stub generation. */
  IR,

  /** Module merging runs as a Kotlin Symbol Processor (KSP) with dagger KSP. */
  KSP,
}
