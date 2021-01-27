package com.squareup.anvil.compiler

import java.lang.reflect.Executable

internal inline fun <T, E : Executable> E.use(block: (E) -> T): T {
  // Deprecated since Java 9, but many projects still use JDK 8 for compilation.
  @Suppress("DEPRECATION")
  val original = isAccessible

  isAccessible = true
  return block(this).also {
    isAccessible = original
  }
}
