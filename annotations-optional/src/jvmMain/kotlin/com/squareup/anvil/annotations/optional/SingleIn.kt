package com.squareup.anvil.annotations.optional

import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.reflect.KClass
import jakarta.inject.Scope as JakartaScope
import javax.inject.Scope as JavaxScope
import me.tatarka.inject.annotations.Scope as KotlinInjectScope

/**
 * Identifies a type that the injector only instantiates once for the given
 * [scope] marker.
 *
 * This can be used in combination with other Anvil annotations to avoid having
 * to manually define scope annotations for each component and to maintain
 * consistency.
 *
 * Component example:
 * ```
 * @SingleIn(AppScope::class)
 * @MergeComponent(AppScope::class)
 * interface AppComponent
 * ```
 *
 * Contribution example:
 * ```
 * interface Authenticator
 *
 * @SingleIn(AppScope::class)
 * @ContributesBinding(AppScope::class)
 * class RealAuthenticator @Inject constructor() : Authenticator
 * ```
 *
 * See Also: [@Scope](Scope)
 */
@JavaxScope
@JakartaScope
@KotlinInjectScope
@Retention(RUNTIME)
public actual annotation class SingleIn(
  /**
   * The marker that identifies this scope.
   */
  actual val scope: KClass<*>,
)
