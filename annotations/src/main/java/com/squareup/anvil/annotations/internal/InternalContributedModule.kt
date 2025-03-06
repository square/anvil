package com.squareup.anvil.annotations.internal

/**
 * An internal annotation that is used to propagate module contribution metadata to downstream compilations.
 * Should not be used directly.
 *
 * @param hints Each hint corresponds to a single contributed module.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
@Repeatable
@InternalAnvilApi
public annotation class InternalContributedModule(
  val hints: Array<String>,
)
