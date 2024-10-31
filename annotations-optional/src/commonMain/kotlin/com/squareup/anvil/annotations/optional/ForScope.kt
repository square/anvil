package com.squareup.anvil.annotations.optional

import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.reflect.KClass
import me.tatarka.inject.annotations.Scope as KotlinInjectScope

/**
 * A class based [qualifier](Qualifier).
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
@KotlinInjectScope
@Retention(RUNTIME)
public expect annotation class ForScope(
  /**
   * The marker that identifies the component in which the annotated object is
   * provided or bound in.
   */
  val scope: KClass<*>,
)
