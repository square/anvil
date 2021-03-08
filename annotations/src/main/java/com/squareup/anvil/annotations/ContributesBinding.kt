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
  /**
   * Whether the qualifier for this class should be included in the generated binding method. This
   * parameter is only necessary to use when [ContributesBinding] and [ContributesMultibinding]
   * are used together for the same class. If not, simply remove the qualifier from the class
   * and don't use this parameter.
   */
  val ignoreQualifier: Boolean = false
)
