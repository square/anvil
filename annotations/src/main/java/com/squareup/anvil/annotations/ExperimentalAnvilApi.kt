package com.squareup.anvil.annotations

import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.annotation.AnnotationTarget.FUNCTION
import kotlin.annotation.AnnotationTarget.PROPERTY

/** Indicates that the annotated Anvil API is experimental and subject to change. */
@RequiresOptIn
@Retention(AnnotationRetention.BINARY)
@Target(CLASS, FUNCTION, PROPERTY)
public annotation class ExperimentalAnvilApi
