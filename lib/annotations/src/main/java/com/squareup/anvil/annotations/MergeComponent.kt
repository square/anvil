package com.squareup.anvil.annotations

import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.reflect.KClass

/**
 * Dagger components that should automatically include Dagger modules and component interfaces for the
 * given [scope] require this annotation instead of `@Component`. The Kotlin compiler plugin will
 * add the `@Component` annotation to this interface. The parameters [modules] and [dependencies] are
 * preserved in the `@Component` annotation.
 *
 * ```
 * @MergeComponent(AppScope::class)
 * interface AppComponent
 * ```
 *
 * It's possible to exclude any automatically added Dagger module or component interface with the
 * [exclude] parameter if needed.
 *
 * ```
 * @MergeComponent(
 *     scope = AppScope::class,
 *     exclude = [
 *       DaggerModule::class,
 *       ComponentInterface::class
 *     ]
 * )
 * interface AppComponent
 * ```
 */
@Target(CLASS)
@Retention(RUNTIME)
@Repeatable
public annotation class MergeComponent(
  /**
   * The scope used to find all contributed bindings, multibindings, modules and component
   * interfaces, which should be included in this component.
   */
  val scope: KClass<*>,
  /**
   * List of Dagger modules that should be manually included in the component and aren't
   * automatically contributed.
   */
  val modules: Array<KClass<*>> = [],
  /**
   * List of types that are to be used as component dependencies.
   */
  val dependencies: Array<KClass<*>> = [],
  /**
   * List of bindings, multibindings, modules and component interfaces that are contributed to the
   * same scope, but should be excluded from the component.
   */
  val exclude: Array<KClass<*>> = [],
)
