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
 *  Imagine this module dependency tree:
 * ```
 *          :app
 *        /   |
 *       v    |
 *   :lib-a   |
 *       \    |
 *        v   v
 *        :lib-b
 * ```
 *
 * `:app` creates the component with `@MergeComponent`, `:lib-a` creates a subcomponent with
 * `@MergeSubcomponent` and `:lib-b` contributes a module to the scope of the subcomponent. This
 * module will be included in the subcomponent because `:lib-a` depends on `:lib-b`.
 *
 * `:lib-a` must depend on `:lib-b` because the subcomponent will only be able to pick up
 * contributions within the scope of the containing Gradle module and is based on the module's
 * compile classpath. This means the module must have a dependency on any modules that want to
 * contribute to the subcomponent.
 *
 * See [ContributesSubcomponent] for an alternative that eliminates the need for `:lib-a` to depend
 * on `:lib-b`
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
@Repeatable
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
