package com.squareup.anvil.compiler.testing

import org.opentest4j.TestAbortedException

/**
 * Skips a Junit test if the predicate is true.
 *
 * There's a corresponding `skipWhen` inspection profile for highlighting these calls in the IDE.
 */
public fun skipWhen(predicate: Boolean, message: () -> String) {
  if (predicate) {
    val msg = message()
    throw TestAbortedException(msg.ifBlank { "Assumption failed" })
  }
}
