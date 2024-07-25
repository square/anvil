package com.squareup.anvil.compiler.internal.reference

import com.squareup.anvil.annotations.ExperimentalAnvilApi

@ExperimentalAnvilApi
public enum class Visibility {
  PUBLIC,
  INTERNAL,
  PROTECTED,
  PRIVATE,
  ;

  public fun isProtectedOrPublic(): Boolean = this == PROTECTED || this == PUBLIC
}
