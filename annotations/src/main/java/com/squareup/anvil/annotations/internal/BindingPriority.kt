package com.squareup.anvil.annotations.internal

import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesBinding.Priority
import com.squareup.anvil.annotations.ContributesBinding.Priority.NORMAL
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.reflect.KClass

/**
 * A hint annotation for Anvil's compiler reference, not considered public API.
 */
@Target(CLASS)
@Retention(RUNTIME)
public annotation class BindingPriority(
  /**
   * The type that this class is bound to. When injecting [boundType] the concrete class will be
   * this annotated class.
   */
  val boundType: KClass<*>,
  /**
   * Corresponds to the [ContributesBinding.priority] this was generated from.
   */
  val priority: Priority = NORMAL,
)
