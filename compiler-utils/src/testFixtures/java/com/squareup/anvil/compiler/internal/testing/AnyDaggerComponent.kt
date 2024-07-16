package com.squareup.anvil.compiler.internal.testing

import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.anvil.annotations.MergeComponent
import com.squareup.anvil.annotations.MergeSubcomponent
import com.squareup.anvil.annotations.compat.MergeModules
import com.squareup.anvil.annotations.internal.InternalMergedTypeMarker
import com.squareup.anvil.compiler.internal.mergedClassName
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.DelicateKotlinPoetApi
import com.squareup.kotlinpoet.asClassName
import kotlin.reflect.KClass

@ExperimentalAnvilApi
public interface AnyDaggerComponent {
  public val modules: List<KClass<*>>
  public val dependencies: List<KClass<*>>
}

@ExperimentalAnvilApi
public fun Class<*>.anyDaggerComponent(annotationClass: KClass<*>): AnyDaggerComponent {
  val classToCheck = resolveIfMerged()
  return when (annotationClass) {
    MergeComponent::class -> object : AnyDaggerComponent {
      override val modules: List<KClass<*>> = classToCheck.daggerComponent.modules.toList()
      override val dependencies: List<KClass<*>> =
        classToCheck.daggerComponent.dependencies.toList()
    }
    MergeSubcomponent::class -> object : AnyDaggerComponent {
      override val modules: List<KClass<*>> = classToCheck.daggerSubcomponent.modules.toList()
      override val dependencies: List<KClass<*>> get() = throw IllegalAccessException()
    }
    MergeModules::class -> object : AnyDaggerComponent {
      override val modules: List<KClass<*>> = classToCheck.daggerModule.includes.toList()
      override val dependencies: List<KClass<*>> get() = throw IllegalAccessException()
    }
    else -> throw IllegalArgumentException("Cannot handle $annotationClass")
  }
}

/**
 * If there's a generated merged component, returns that [Class]. This would imply that this was
 * generated under KSP. Otherwise, returns this [Class].
 */
@ExperimentalAnvilApi
public fun Class<*>.resolveIfMerged(): Class<*> = generatedMergedComponentOrNull() ?: this

/**
 * If there's a generated merged component, returns that [Class]. This would imply that this was
 * generated under KSP.
 */
@OptIn(DelicateKotlinPoetApi::class)
@ExperimentalAnvilApi
public fun Class<*>.generatedMergedComponentOrNull(): Class<*>? {
  return try {
    val currentClassName = if (packageName().isEmpty()) {
      ClassName("", canonicalName.split("."))
    } else {
      asClassName()
    }
    val expected = currentClassName.mergedClassName().reflectionName()
    classLoader.loadClass(expected)
  } catch (e: ClassNotFoundException) {
    null
  }
}

/**
 * The inverse of [resolveIfMerged], returning the original root type if this class is a merged
 * component.
 */
@ExperimentalAnvilApi
public fun Class<*>.originIfMerged(): Class<*> = originOfMergedComponentOrNull() ?: this

/**
 * The inverse of [generatedMergedComponentOrNull], returning the original root type if this class
 * is a merged component.
 */
@ExperimentalAnvilApi
public fun Class<*>.originOfMergedComponentOrNull(): Class<*>? {
  return getDeclaredAnnotation(InternalMergedTypeMarker::class.java)?.originClass?.java
}
