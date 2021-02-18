package com.squareup.anvil.compiler

import java.lang.reflect.Member
import java.lang.reflect.Modifier

val Member.isStatic: Boolean get() = Modifier.isStatic(modifiers)
val Member.isAbstract: Boolean get() = Modifier.isAbstract(modifiers)

/**
 * Creates a new instance of this class with the given arguments. This method assumes that this
 * class only declares a single constructor.
 */
@Suppress("UNCHECKED_CAST")
fun <T : Any> Class<T>.createInstance(
  vararg initargs: Any?
): T = declaredConstructors.single().use { it.newInstance(*initargs) } as T
