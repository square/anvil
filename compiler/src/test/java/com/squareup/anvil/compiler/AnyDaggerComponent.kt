package com.squareup.anvil.compiler

import com.squareup.anvil.annotations.MergeComponent
import com.squareup.anvil.annotations.MergeSubcomponent
import kotlin.reflect.KClass

internal interface AnyDaggerComponent {
  val modules: List<KClass<*>>
  val dependencies: List<KClass<*>>
}

internal fun Class<*>.anyDaggerComponent(annotationClass: KClass<*>): AnyDaggerComponent {
  return when (annotationClass) {
    MergeComponent::class -> object : AnyDaggerComponent {
      override val modules: List<KClass<*>> = daggerComponent.modules.toList()
      override val dependencies: List<KClass<*>> = daggerComponent.dependencies.toList()
    }
    MergeSubcomponent::class -> object : AnyDaggerComponent {
      override val modules: List<KClass<*>> = daggerSubcomponent.modules.toList()
      override val dependencies: List<KClass<*>> get() = throw IllegalAccessException()
    }
    else -> throw IllegalArgumentException("Cannot handle $annotationClass")
  }
}
