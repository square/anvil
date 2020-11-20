package com.squareup.anvil.annotations

import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.reflect.KClass

/**
 * Dagger subcomponents that should automatically include Dagger modules and component interfaces for
 * the given [scope] require this annotation instead of `@Subcomponent`. The Kotlin compiler plugin
 * will add the `@Subcomponent` annotation to this interface. The [modules] parameter is
 * preserved in the `@Subcomponent` annotation.
 *
 * ```
 * @MergeSubcomponent(ActivityScope::class)
 * interface ActivitySubcomponent
 * ```
 *
 * It's possible to exclude any automatically added Dagger module or component interface with the
 * [exclude] parameter if needed.
 *
 * ```
 * @MergeSubcomponent(
 *     scope = ActivityScope::class,
 *     exclude = [
 *       DaggerModule::class,
 *       ComponentInterface::class
 *     ]
 * )
 * interface ActivitySubcomponent
 * ```
 */

@Target(CLASS)
@Retention(RUNTIME)
public annotation class MergeSubcomponent(
  val scope: KClass<*>,
  val modules: Array<KClass<*>> = [],
  val exclude: Array<KClass<*>> = []
)
