package com.squareup.anvil.compiler.api

public interface AnvilApplicabilityChecker {
  /**
   * Returns true if this code generator is applicable for the given [context] or false if not. This
   * will only be called _once_.
   */
  public fun isApplicable(context: AnvilContext): Boolean
}
