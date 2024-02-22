package com.squareup.anvil.annotations.internal

import com.squareup.anvil.annotations.ContributesBinding.Priority
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.CLASS

/**
 * An internal Anvil API used to propagate binding metadata from contributed bindings.
 * Should not be used directly.
 *
 * Note the reader of this should also check the single binds function in the annotated module to
 * read the bound type and possible qualifier.
 *
 * @param Origin the origin class that contributed the binding. This is important to include because
 *               the contributed binding may be an `object` and cannot be specified as a binding
 *               parameter. This does not participate in the public API though!
 * @param isMultibinding Whether this is a multibinding.
 * @param priority The priority of the contributed binding. Corresponds to a [Priority.name].
 * @param qualifierKey The computed key of the qualifier annotation if present. Empty otherwise.
 */
@Target(CLASS)
@Retention(RUNTIME)
@Repeatable
public annotation class InternalBindingMarker<Origin>(
  val isMultibinding: Boolean,
  val priority: String = "",
  val qualifierKey: String = "",
)
