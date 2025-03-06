package com.squareup.anvil.annotations.internal

/** This Anvil API is meant for internal use and is subject to change. */
@RequiresOptIn("This Anvil API is meant for internal use and is subject to change.")
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
public annotation class InternalAnvilApi
