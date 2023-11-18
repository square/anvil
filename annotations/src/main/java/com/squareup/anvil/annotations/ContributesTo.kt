package com.squareup.anvil.annotations

import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.reflect.KClass

/**
 * Marks a Dagger module or component interface to be included in the Dagger dependency graph in the
 * given [scope]. Anvil automatically adds the module to the list of included modules in the
 * Dagger component marked with [MergeComponent]. For component interfaces it will make the Dagger
 * component extend the interface.
 *
 * ```
 * @Module
 * @ContributesTo(AppScope::class)
 * class DaggerModule { .. }
 *
 * @ContributesTo(LoggedInScope::class)
 * interface ComponentInterface {
 *   fun provideSomething()
 * }
 * ```
 *
 * Modules and component interfaces can replace other modules and component interfaces with the
 * [replaces] parameter. This is especially helpful for modules providing different bindings in
 * instrumentation tests.
 * ```
 * @Module
 * @ContributesTo(
 *     AppScope::class,
 *     replaces = [DevelopmentApplicationModule::class]
 * )
 * object DevelopmentApplicationTestModule {
 *   @Provides
 *   fun provideEndpointSelector(): EndpointSelector = TestingEndpointSelector
 * }
 * ```
 */
@Target(CLASS)
@Retention(RUNTIME)
@Repeatable
public annotation class ContributesTo(
  /**
   * The scope in which to include this module.
   */
  val scope: KClass<*>,
  /**
   * This contributed module will replace these contributed classes. The array is allowed to
   * include other contributed bindings, multibindings and Dagger modules. All replaced classes
   * must use the same scope.
   */
  val replaces: Array<KClass<*>> = [],
)
