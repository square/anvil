package com.squareup.anvil.annotations.internal

import kotlin.reflect.KClass

/**
 * Metadata about the origin of a merged type. Useful for testing and can be discarded in production.
 */
@Target(AnnotationTarget.CLASS)
public annotation class InternalMergedTypeMarker(
  val originClass: KClass<*>,
  val scope: KClass<*>,
)
