package com.squareup.anvil.compiler.testing.temp

import com.squareup.anvil.annotations.ExperimentalAnvilApi
import java.lang.reflect.AccessibleObject

@ExperimentalAnvilApi
public inline fun <T, E : AccessibleObject> E.use(block: (E) -> T): T {
  // Deprecated since Java 9, but many projects still use JDK 8 for compilation.
  @Suppress("DEPRECATION")
  val original = isAccessible

  return try {
    isAccessible = true
    block(this)
  } finally {
    isAccessible = original
  }
}
