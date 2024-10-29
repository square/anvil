@file:Suppress("unused")

package com.squareup.anvil.compiler.internal.testing

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.anvil.annotations.internal.InternalBindingMarker
import com.squareup.anvil.compiler.internal.capitalize
import dagger.Component
import dagger.Module
import dagger.Provides
import dagger.Subcomponent
import org.jetbrains.kotlin.utils.mapToSetOrEmpty
import kotlin.reflect.KClass

@ExperimentalAnvilApi
public fun Class<*>.moduleFactoryClass(
  providerMethodName: String? = null,
  companion: Boolean = false,
): Class<*> {
  val companionString = if (companion) "_Companion" else ""

  val methodsOrCompanionMethods = if (companion) {
    fields.single { it.name == "Companion" }.type.methods
  } else {
    methods
  }

  val providesMethods = methodsOrCompanionMethods
    .filter { it.isAnnotationPresent(Provides::class.java) }
    .mapToSetOrEmpty { it.name }

  assertWithMessage("No @Provides methods found in $this")
    .that(providesMethods)
    .isNotEmpty()

  if (providerMethodName != null) {
    assertWithMessage(
      "The name '$providerMethodName' must match a function annotated with @Provides",
    )
      .that(providesMethods)
      .contains(providerMethodName)
  } else {
    assertWithMessage(
      "You must specify a providerMethodName value when there is more than one @Provides function",
    )
      .that(providesMethods)
      .hasSize(1)
  }

  val methodName = providerMethodName ?: providesMethods.single()

  return classLoader.loadClass(
    "${generatedClassesString()}${companionString}_${methodName.capitalize()}Factory",
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
  separator: String = "_",
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
public infix fun Class<*>.shouldNotExtend(other: Class<*>) {
  assertWithMessage("Expected $this to not extend $other")
    .that(other.isAssignableFrom(this))
    .isFalse()
}

@ExperimentalAnvilApi
public infix fun KClass<*>.extends(other: KClass<*>): Boolean =
  other.java.isAssignableFrom(this.java)

@Deprecated(
  "Use withoutAnvilModules() instead",
  ReplaceWith(
    "withoutAnvilModules()",
    imports = ["com.squareup.anvil.compiler.internal.testing.withoutAnvilModules"],
  ),
)
@ExperimentalAnvilApi
public fun Array<KClass<*>>.withoutAnvilModule(): List<KClass<*>> = withoutAnvilModules()

@Deprecated(
  "Use withoutAnvilModules() instead",
  ReplaceWith(
    "withoutAnvilModules()",
    imports = ["com.squareup.anvil.compiler.internal.testing.withoutAnvilModules"],
  ),
)
@ExperimentalAnvilApi
public fun Collection<KClass<*>>.withoutAnvilModule(): List<KClass<*>> =
  withoutAnvilModules()

@ExperimentalAnvilApi
public fun Array<KClass<*>>.withoutAnvilModules(): List<KClass<*>> = toList().withoutAnvilModules()

@ExperimentalAnvilApi
public fun Collection<KClass<*>>.withoutAnvilModules(): List<KClass<*>> =
  filterNot {
    it.qualifiedName!!.startsWith("anvil.module") || it.java.isAnnotationPresent(
      InternalBindingMarker::class.java,
    )
  }

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
