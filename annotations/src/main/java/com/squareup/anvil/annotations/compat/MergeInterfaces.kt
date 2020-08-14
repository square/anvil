package com.squareup.anvil.annotations.compat

import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.reflect.KClass

/**
 * Marks an interface to merge all contributed component interfaces for the given [scope]. This is a
 * subset of what [MergeComponent] and [MergeSubcomponent] are doing.
 *
 * This annotation is useful if you can't use [MergeComponent] and [MergeSubcomponent] directly for
 * your Dagger component. The interface annotated with this annotation serves as a composite
 * component interface.
 *
 * ```
 * @MergeInterfaces(AppScope::class)
 * interface CompositeAppComponent
 * ```
 *
 * One common scenario where this annotation is used is when you want to use javac and not KAPT for
 * compiling your Dagger components. The recommendation there is to add an extra Gradle module that
 * merges interfaces. Your Dagger component must manually extend this interface using this annotation.
 *
 * ```
 * // Gradle Module A.
 * @MergeInterfaces(AppScope::class)
 * interface CompositeAppComponent
 *
 * // Gradle Module B, note that this is Java and not Kotlin.
 * @Component
 * public interface AppComponent extends CompositeAppComponent {}
 * ```
 *
 * It's possible to exclude any automatically added component interface with the [exclude] parameter
 * if needed.
 *
 * ```
 * @MergeInterfaces(
 *     scope = AppScope::class,
 *     exclude = [
 *       ComponentInterface::class
 *     ]
 * )
 * interface CompositeAppComponent
 * ```
 */

@Target(CLASS)
@Retention(RUNTIME)
public annotation class MergeInterfaces(
  val scope: KClass<*>,
  val exclude: Array<KClass<*>> = []
)
