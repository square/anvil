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
public annotation class MergeComponent(
  val scope: KClass<*>,
  val modules: Array<KClass<*>> = [],
  val dependencies: Array<KClass<*>> = [],
  val exclude: Array<KClass<*>> = []
)
