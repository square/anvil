package com.squareup.anvil.compiler.api

public fun interface AnvilApplicabilityChecker {
  /**
   * Returns true if this code generator is applicable for the given [context] or false if not. This
   * will only be called _once_.
   */
  public fun isApplicable(context: AnvilContext): Boolean

  public companion object {
    /** Returns an instance that always returns true. */
    public fun always(): AnvilApplicabilityChecker = Always
  }

  private object Always : AnvilApplicabilityChecker {
    override fun isApplicable(context: AnvilContext): Boolean = true
  }
}
