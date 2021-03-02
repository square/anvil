package com.squareup.anvil.annotations

import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.reflect.KClass

/**
 * Generate a Dagger multibinding method for an annotated class and contributes this multibinding
 * method to the given [scope]. Imagine this example:
 * ```
 * interface Listener
 *
 * class MainListener @Inject constructor() : Listener
 *
 * @Module
 * @ContributesTo(AppScope::class)
 * abstract class MainListenerModule {
 *   @Binds @IntoSet
 *   abstract fun bindMainListener(listener: MainListener): Listener
 * }
 * ```
 * This is a lot of boilerplate. You can replace this entire module with the
 * [ContributesMultibinding] annotation. The equivalent would be:
 * ```
 * interface Listener
 *
 * @ContributesMultibinding(AppScope::class)
 * class MainListener @Inject constructor() : Listener
 * ```
 * Notice that it's optional to specify [boundType], if there is only exactly one super type. If
 * there are multiple super types, then it's required to specify the parameter:
 * ```
 * @ContributesMultibinding(
 *   scope = AppScope::class,
 *   boundType = Listener::class
 * )
 * class MainListener @Inject constructor() : Activity(), Listener
 * ```
 *
 * [ContributesMultibinding] supports qualifiers. If you annotate the class additionally with a
 * qualifier, then the generated multibinding method will be annotated with the same qualifier,
 * e.g.
 * ```
 * @ContributesMultibinding(AppScope::class)
 * @Named("Prod")
 * class MainListener @Inject constructor() : Listener
 *
 * // Will generate this binding method.
 * @Binds @IntoSet @Named("Prod")
 * abstract fun bindMainListener(listener: MainListener): Listener
 * ```
 *
 * Contributed multibindings can replace other contributed modules and contributed multibindings
 * with the [replaces] parameter. This is especially helpful for different multibindings in
 * tests.
 * ```
 * @ContributesMultibinding(
 *   scope = AppScope::class,
 *   replaces = [MainListener::class]
 * )
 * class FakeListener @Inject constructor() : Listener
 * ```
 * [ContributesMultibinding] supports Kotlin objects, e.g.
 * ```
 * @ContributesMultibinding(AppScope::class)
 * object MainListener : Listener
 * ```
 * In this scenario instead of generating a `@Binds @IntoSet` method Anvil will generate a
 * `@Provides @IntoSet` method returning [boundType].
 *
 * [ContributesMultibinding] can be used in conjunction with [ContributesBinding]. Each annotation
 * will generate the respective binding or multibindings method. If a qualifier is only meant for
 * one of the annotations, then you can set [ignoreQualifier] to `true` and the qualifier won't
 * be added to the generated multibinding method.
 * ```
 * @ContributesBinding(AppScope::class)
 * @ContributesMultibinding(AppScope::class, ignoreQualifier = true)
 * @Named("Prod")
 * object MainListener : Listener
 * object MainListener : Listener
 * ```
 */
@Target(CLASS)
@Retention(RUNTIME)
public annotation class ContributesMultibinding(
  val scope: KClass<*>,
  val boundType: KClass<*> = Unit::class,
  val replaces: Array<KClass<*>> = [],
  val ignoreQualifier: Boolean = false
)
