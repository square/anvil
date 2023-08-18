package com.squareup.anvil.annotations.optional

import javax.inject.Qualifier
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.reflect.KClass

/**
 * A class based [qualfier](Qualifier).
 *
 * This can be used in combination with other Anvil annotations to avoid having
 * to manually define qualifier annotations for each component and to maintain
 * consistency.
 *
 * Example:
 * ```
 * interface Authenticator
 *
 * @ForScope(AppScope::class)
 * @ContributesBinding(AppScope::class)
 * class CommonAuthenticator @Inject constructor() : Authenticator
 *
 * @ForScope(UserScope::class)
 * @ContributesBinding(UserScope::class)
 * class UserAuthenticator @Inject constructor() : Authenticator
 * ```
 */
@Qualifier
@Retention(RUNTIME)
public annotation class ForScope(
  /**
   * The marker that identifies the component in which the annotated object is
   * provided or bound in.
   */
  val scope: KClass<*>,
)
