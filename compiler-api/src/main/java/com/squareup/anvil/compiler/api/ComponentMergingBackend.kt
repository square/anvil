package com.squareup.anvil.compiler.api

import java.util.Locale

/** Possible modes of component merging. */
public enum class ComponentMergingBackend {
  /** Component merging runs as an IR plugin during kapt stub generation. */
  IR,

  /** Component merging runs as a Kotlin Symbol Processor (KSP) with dagger KSP. */
  KSP,

  ;

  public companion object {
    public fun fromString(value: String): ComponentMergingBackend? {
      val uppercase = value.uppercase(Locale.US)
      return ComponentMergingBackend.entries.find { it.name == uppercase }
    }
  }
}
