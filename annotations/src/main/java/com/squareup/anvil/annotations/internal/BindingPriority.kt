package com.squareup.anvil.annotations.internal

import com.squareup.anvil.annotations.ContributesBinding.Priority
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.reflect.KClass

/**
 * An internal Anvil API used to propagate [priority] from contributed bindings.
 * Should not be used directly.
 */
@Target(CLASS)
@Retention(RUNTIME)
@Repeatable
public annotation class BindingPriority(val scope: KClass<*>, val priority: Priority)
