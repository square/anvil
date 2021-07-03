@file:Suppress("unused")

package com.squareup.anvil.compiler.internal.testing

import com.squareup.anvil.annotations.ExperimentalAnvilApi
import java.lang.reflect.AccessibleObject
import java.lang.reflect.Member
import java.lang.reflect.Modifier

@ExperimentalAnvilApi
public val Member.isStatic: Boolean get() = Modifier.isStatic(modifiers)
@ExperimentalAnvilApi
public val Member.isAbstract: Boolean get() = Modifier.isAbstract(modifiers)

/**
 * Creates a new instance of this class with the given arguments. This method assumes that this
 * class only declares a single constructor.
 */
@ExperimentalAnvilApi
@Suppress("UNCHECKED_CAST")
public fun <T : Any> Class<T>.createInstance(
  vararg initargs: Any?
): T = declaredConstructors.single().use { it.newInstance(*initargs) } as T

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
