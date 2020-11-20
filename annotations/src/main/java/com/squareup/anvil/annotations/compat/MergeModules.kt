package com.squareup.anvil.annotations.compat

import com.squareup.anvil.annotations.MergeComponent
import com.squareup.anvil.annotations.MergeSubcomponent
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.reflect.KClass

/**
 * Marks a Dagger module to merge all contributed Dagger modules for the given [scope]. This is a
 * subset of what [MergeComponent] and [MergeSubcomponent] are doing. Use this annotation instead of
 * `@Module` in your module. The Kotlin compiler plugin will add the `@Module` annotation to this
 * class. The parameters [includes] and [subcomponents] are preserved in the `@Module` annotation.
 *
 * This annotation is useful if you can't use [MergeComponent] and [MergeSubcomponent] directly for
 * your Dagger component. The Dagger modules annotated with this annotation serve as a composite
 * module.
 *
 * ```
 * @MergeModules(AppScope::class)
 * abstract class CompositeAppModule
 * ```
 *
 * One common scenario where this annotation is used is when you want to use javac and not KAPT for
 * compiling your Dagger components. The recommendation there is to add an extra Gradle module that
 * merges modules. Your Dagger component must manually include this module using this annotation.
 *
 * ```
 * // Gradle Module A.
 * @MergeModules(AppScope::class)
 * abstract class CompositeAppModule
 *
 * // Gradle Module B, note that this is Java and not Kotlin.
 * @Component(modules = CompositeAppModule.class)
 * public interface AppComponent {}
 * ```
 *
 * It's possible to exclude any automatically added Dagger module with the [exclude] parameter
 * if needed.
 *
 * ```
 * @MergeModules(
 *     scope = AppScope::class,
 *     exclude = [
 *       DaggerModule::class
 *     ]
 * )
 * abstract class CompositeAppModule
 * ```
 */

@Target(CLASS)
@Retention(RUNTIME)
public annotation class MergeModules(
  val scope: KClass<*>,
  val includes: Array<KClass<*>> = [],
  val subcomponents: Array<KClass<*>> = [],
  val exclude: Array<KClass<*>> = []
)
