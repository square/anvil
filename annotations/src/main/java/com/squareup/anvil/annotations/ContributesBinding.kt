package com.squareup.anvil.annotations

import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.reflect.KClass

/**
 * Generate a Dagger binding method for an annotated class and contributes this binding method to
 * the given [scope]. Imagine this example:
 * ```
 * interface Authenticator
 *
 * class RealAuthenticator @Inject constructor() : Authenticator
 *
 * @Module
 * @ContributesTo(AppScope::class)
 * abstract class AuthenticatorModule {
 *   @Binds abstract fun bindRealAuthenticator(authenticator: RealAuthenticator): Authenticator
 * }
 * ```
 * This is a lot of boilerplate if you always want to use `RealAuthenticator` when injecting
 * `Authenticator`. You can replace this entire module with the [ContributesBinding] annotation.
 * The equivalent would be:
 * ```
 * interface Authenticator
 *
 * @ContributesBinding(AppScope::class)
 * class RealAuthenticator @Inject constructor() : Authenticator
 * ```
 * Notice that it's optional to specify [boundType], if there is only exactly one super type. If
 * there are multiple super types, then it's required to specify the parameter:
 * ```
 * @ContributesBinding(
 *     scope = AppScope::class,
 *     boundType = Authenticator::class
 * )
 * class RealAuthenticator @Inject constructor() : AbstractTokenProvider(), Authenticator
 * ```
 *
 * [ContributesBinding] supports qualifiers. If you annotate the class additionally with a
 * qualifier, then the generated binding method will be annotated with the same qualifier, e.g.
 * ```
 * @ContributesBinding(AppScope::class)
 * @Named("Prod")
 * class RealAuthenticator @Inject constructor() : Authenticator
 *
 * // Will generate this binding method.
 * @Binds @Named("Prod")
 * abstract fun bindRealAuthenticator(authenticator: RealAuthenticator): Authenticator
 * ```
 *
 * [ContributesMultibinding] allows you to generate multibinding methods. Both annotations can be
 * used in conjunction. If a qualifier is only meant for one of the annotations, then you can set
 * [ignoreQualifier] to `true` and the qualifier won't be added to the generated binding method.
 * ```
 * @ContributesBinding(AppScope::class, ignoreQualifier = true)
 * @ContributesMultibinding(AppScope::class)
 * @Named("Prod")
 * object MainListener : Listener
 * ```
 *
 * [ContributesBinding] is a convenience for a very simple but the most common scenario. Multiple
 * bound types or generic types are not supported. In these cases it's still required to write
 * a Dagger module.
 *
 * Contributed bindings can replace other contributed modules and bindings with the [replaces]
 * parameter. This is especially helpful for different bindings in instrumentation tests.
 * ```
 * @ContributesBinding(
 *     scope = AppScope::class,
 *     replaces = [RealAuthenticator::class]
 * )
 * class FakeAuthenticator @Inject constructor() : Authenticator
 * ```
 * If you don't have access to the class of another contributed binding that you want to replace,
 * then you can change the [priority] of the bindings to avoid duplicate bindings. The contributed
 * binding with the higher priority will be used.
 *
 * [ContributesBinding] supports Kotlin objects, e.g.
 * ```
 * @ContributesBinding(AppScope::class)
 * object RealAuthenticator : Authenticator
 * ```
 * In this scenario instead of generating a `@Binds` method Anvil will generate a `@Provides`
 * method returning [boundType].
 */
@Target(CLASS)
@Retention(RUNTIME)
@Repeatable
public annotation class ContributesBinding(
  /**
   * The scope in which to include this contributed binding.
   */
  val scope: KClass<*>,
  /**
   * The type that this class is bound to. When injecting [boundType] the concrete class will be
   * this annotated class.
   */
  val boundType: KClass<*> = Unit::class,
  /**
   * This contributed binding will replace these contributed classes. The array is allowed to
   * include other contributed bindings or contributed Dagger modules. All replaced classes must
   * use the same scope.
   */
  val replaces: Array<KClass<*>> = [],
  @Suppress("DEPRECATION")
  @Deprecated("Use the new int-based priority", ReplaceWith("priority"))
  @get:JvmName("priority")
  val priorityDeprecated: Priority = Priority.NORMAL,
  /**
   * Whether the qualifier for this class should be included in the generated binding method. This
   * parameter is only necessary to use when [ContributesBinding] and [ContributesMultibinding]
   * are used together for the same class. If not, simply remove the qualifier from the class
   * and don't use this parameter.
   */
  val ignoreQualifier: Boolean = false,
  /**
   * The priority of this contributed binding. The priority should be changed only if you don't
   * have access to the contributed binding class that you want to replace at compile time. If
   * you have access and can reference the other class, then it's highly suggested to
   * use [replaces] instead.
   *
   * In case of a duplicate binding for multiple contributed bindings the binding with the highest
   * priority will be used and replace other contributed bindings for the same type with a lower
   * priority. If duplicate contributed bindings use the same priority, then there will be an
   * error for duplicate bindings.
   *
   * Note that [replaces] takes precedence. If you explicitly replace a binding it won't be
   * considered no matter what its priority is.
   *
   * All contributed bindings have a [PRIORITY_NORMAL] priority by default.
   */
  @get:JvmName("priorityInt")
  val priority: Int = PRIORITY_NORMAL,
) {

  public companion object {
    public const val PRIORITY_NORMAL: Int = Int.MIN_VALUE
    public const val PRIORITY_HIGH: Int = 0
    public const val PRIORITY_HIGHEST: Int = Int.MAX_VALUE
  }

  /**
   * The priority of a contributed binding.
   */
  @Deprecated("Use the new int-based priority")
  @Suppress("unused")
  public enum class Priority(public val value: Int) {
    NORMAL(PRIORITY_NORMAL),
    HIGH(PRIORITY_HIGH),
    HIGHEST(PRIORITY_HIGHEST),
  }
}
