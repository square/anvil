package com.squareup.anvil.compiler.testing.reflect

import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.anvil.annotations.ContributesTo
import kotlin.reflect.KClass

public val Annotation.scope: KClass<*>
  get() {
    return when (this) {
      is ContributesTo -> scope
      is ContributesBinding -> scope
      is ContributesMultibinding -> scope
      else -> error("Unknown annotation class: $this")
    }
  }

public val Annotation.boundType: KClass<*>
  get() {
    return when (this) {
      is ContributesBinding -> boundType
      is ContributesMultibinding -> boundType
      else -> error("Unknown annotation class: $this")
    }
  }
