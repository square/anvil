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
  /**
   * The scope used to find all contributed bindings, multibindings, modules and component
   * interfaces, which should be included in this subcomponent.
   */
  val scope: KClass<*>,
  /**
   * List of Dagger modules that should be manually included in the subcomponent and aren't
   * automatically contributed.
   */
  val modules: Array<KClass<*>> = [],
  /**
   * List of bindings, multibindings, modules and component interfaces that are contributed to the
   * same scope, but should be excluded from the subcomponent.
   */
  val exclude: Array<KClass<*>> = []
)
