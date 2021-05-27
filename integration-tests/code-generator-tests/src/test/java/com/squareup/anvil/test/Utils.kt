package com.squareup.anvil.test

import kotlin.reflect.KClass

internal infix fun KClass<*>.extends(other: KClass<*>): Boolean =
  other.java.isAssignableFrom(this.java)
