package com.squareup.anvil.compiler.api

/** Possible modes of component merging. */
public enum class ComponentMergingBackend {
  /** Component merging runs as an IR plugin during kapt stub generation. */
  IR,

  /** Component merging runs as a Kotlin Symbol Processor (KSP) with dagger KSP. */
  KSP,
}
