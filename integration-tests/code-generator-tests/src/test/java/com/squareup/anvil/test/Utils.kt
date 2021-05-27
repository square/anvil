package com.squareup.anvil.test

import kotlin.reflect.KClass

internal infix fun KClass<*>.extends(other: KClass<*>): Boolean =
  other.java.isAssignableFrom(this.java)

internal fun Array<KClass<*>>.withoutAnvilModule(): List<KClass<*>> = toList().withoutAnvilModule()
internal fun Collection<KClass<*>>.withoutAnvilModule(): List<KClass<*>> =
  filterNot { it.qualifiedName!!.startsWith("anvil.module") }
