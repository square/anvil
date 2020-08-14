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
 * [ContributesBinding] is a convenience for a very simple but the most common scenario. Multiple
 * bound types, generic types, multibindings or qualifiers are not supported. In these cases it's
 * still required to write a Dagger module.
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
  val scope: KClass<*>,
  val boundType: KClass<*> = Unit::class,
  val replaces: Array<KClass<*>> = []
)
