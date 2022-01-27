@file:Suppress("unused")

package com.squareup.anvil.compiler.internal.testing

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.anvil.compiler.internal.capitalize
import dagger.Component
import dagger.Module
import dagger.Subcomponent
import kotlin.reflect.KClass

@ExperimentalAnvilApi
public fun Class<*>.moduleFactoryClass(
  providerMethodName: String,
  companion: Boolean = false
): Class<*> {
  val companionString = if (companion) "_Companion" else ""
  return classLoader.loadClass(
    "${generatedClassesString()}${companionString}_${providerMethodName.capitalize()}Factory"
  )
}

@ExperimentalAnvilApi
public fun Class<*>.factoryClass(): Class<*> {
  return classLoader.loadClass("${generatedClassesString()}_Factory")
}

@ExperimentalAnvilApi
public fun Class<*>.implClass(): Class<*> {
  return classLoader.loadClass("${generatedClassesString()}_Impl")
}

@ExperimentalAnvilApi
public fun Class<*>.membersInjector(): Class<*> {
  return classLoader.loadClass("${generatedClassesString()}_MembersInjector")
}

@ExperimentalAnvilApi
public fun Class<*>.generatedClassesString(
  separator: String = "_"
): String {
  return generateSequence(enclosingClass) { it.enclosingClass }
    .toList()
    .reversed()
    .joinToString(separator = "", prefix = packageName(), postfix = simpleName) {
      "${it.simpleName}$separator"
    }
}

@ExperimentalAnvilApi
public fun Class<*>.packageName(): String = `package`.name.let {
  if (it.isBlank()) "" else "$it."
}

@ExperimentalAnvilApi
public val Class<*>.daggerComponent: Component
  get() = annotations.filterIsInstance<Component>()
    .also { assertThat(it).hasSize(1) }
    .first()

@ExperimentalAnvilApi
public val Class<*>.daggerSubcomponent: Subcomponent
  get() = annotations.filterIsInstance<Subcomponent>()
    .also { assertThat(it).hasSize(1) }
    .first()

@ExperimentalAnvilApi
public val Class<*>.daggerModule: Module
  get() = annotations.filterIsInstance<Module>()
    .also { assertThat(it).hasSize(1) }
    .first()

@ExperimentalAnvilApi
public infix fun Class<*>.extends(other: Class<*>): Boolean = other.isAssignableFrom(this)

@ExperimentalAnvilApi
public infix fun KClass<*>.extends(other: KClass<*>): Boolean =
  other.java.isAssignableFrom(this.java)

@ExperimentalAnvilApi
public fun Array<KClass<*>>.withoutAnvilModule(): List<KClass<*>> = toList().withoutAnvilModule()

@ExperimentalAnvilApi
public fun Collection<KClass<*>>.withoutAnvilModule(): List<KClass<*>> =
  filterNot { it.qualifiedName!!.startsWith("anvil.module") }

@ExperimentalAnvilApi
public fun Any.invokeGet(vararg args: Any?): Any {
  val method = this::class.java.declaredMethods.single { it.name == "get" }
  return method.invoke(this, *args)
}

@ExperimentalAnvilApi
public fun Any.getPropertyValue(name: String): Any {
  return this::class.java.fields.first { it.name == name }.use { it.get(this) }
}

@Suppress("UNCHECKED_CAST")
@ExperimentalAnvilApi
public fun <T> Annotation.getValue(): T =
  this::class.java.declaredMethods.single { it.name == "value" }.invoke(this) as T
