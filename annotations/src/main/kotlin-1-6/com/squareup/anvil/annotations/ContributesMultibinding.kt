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
 * To generate a Map multibindings method you need to annotate the class with the map key. Anvil
 * will use the map key as hint to generate a binding method for a map instead of a set:
 * ```
 * @MapKey
 * annotation class BindingKey(val value: String)
 *
 * @ContributesMultibinding(AppScope::class)
 * @BindingKey("abc")
 * class MainListener @Inject constructor() : Listener
 *
 * // Will generate this binding method.
 * @Binds @IntoMap @BindingKey("abc")
 * abstract fun bindMainListener(listener: MainListener): Listener
 * ```
 * Note that map keys must allow classes as target. Dagger's built-in keys like `@StringKey` and
 * `@ClassKey` only allow methods as target and therefore cannot be used with this annotation. It's
 * recommended to create your own map key annotation class like in the snippet above. Furthermore,
 * Dagger allows using map keys that are not known at compile time. Anvil doesn't support them
 * either and it's recommended to contribute a normal Dagger module to the graph instead of using
 * [ContributesMultibinding].
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
  /**
   * The scope in which to include this contributed multibinding.
   */
  val scope: KClass<*>,
  /**
   * The type that this class is bound to. This class will be included in the collection for
   * [boundType].
   */
  val boundType: KClass<*> = Unit::class,
  /**
   * This contributed multibinding will replace these contributed classes. The array is allowed to
   * include other contributed multibindings or contributed Dagger modules. All replaced classes
   * must use the same scope.
   */
  val replaces: Array<KClass<*>> = [],
  /**
   * Whether the qualifier for this class should be included in the generated multibinding method.
   * This parameter is only necessary to use when [ContributesBinding] and [ContributesMultibinding]
   * are used together for the same class. If not, simply remove the qualifier from the class and
   * don't use this parameter.
   */
  val ignoreQualifier: Boolean = false
)
