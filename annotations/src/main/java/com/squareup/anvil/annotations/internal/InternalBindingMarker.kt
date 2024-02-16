package com.squareup.anvil.annotations.internal

import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.CLASS

/**
 * An internal Anvil API used to propagate binding metadata from contributed bindings.
 * Should not be used directly.
 *
 * Note the reader of this should also check the single binds function in the annotated module to
 * read the bound type and possible qualifier.
 *
 * @param isMultibinding Whether this is a multibinding.
 * @param priority The priority of the contributed binding. Corresponds to a [Priority.name].
 * @param qualifierKey The computed key of the qualifier annotation if present. Empty otherwise.
 */
@Target(CLASS)
@Retention(RUNTIME)
@Repeatable
public annotation class InternalBindingMarker(
  val isMultibinding: Boolean,
  val priority: String = "",
  val qualifierKey: String = "",
)
