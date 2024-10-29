package com.squareup.anvil.annotations.internal

import com.squareup.anvil.annotations.ContributesSubcomponent
import kotlin.reflect.KClass

/**
 * When using [ContributesSubcomponent], we generate some extra information based on the source
 * class.
 *
 * There are three types of contribution supported.
 *
 * ## Factory
 *
 * ```kotlin
 * @ContributesSubcomponent(UserScope::class, parentScope = AppScope::class)
 * interface UserSubcomponent {
 *   @ContributesSubcomponent.Factory
 *   interface ComponentFactory {
 *     fun createComponent(
 *       @BindsInstance integer: Int
 *     ): UserSubcomponent
 *   }
 *
 *   fun integer(): Int
 * }
 * ```
 *
 * In this case, [componentFactory] will be set to the `ComponentFactory` interface and a parent
 * component interface will be generated to contribute the factory to the parent scope. This in
 * turn will generate code like so.
 *
 * ```kotlin
 * // Intermediate mergeable class
 * @MergeSubcomponent(scope = UserScope::class)
 * @InternalContributedSubcomponentMarker(
 *   originClass = UserSubcomponent::class,
 *   componentFactory = UserSubcomponent.ComponentFactory::class,
 * )
 * public interface UserSubcomponent_12c9cde5 : UserSubcomponent {
 *   @Module
 *   public interface BindingModule {
 *     @Binds
 *     public fun bindUserSubcomponent_12c9cde5(impl: UserSubcomponent_12c9cde5): UserSubcomponent
 *   }
 *
 *   public interface ParentComponent {
 *     public fun createComponentFactory(): UserSubcomponent.ComponentFactory
 *   }
 * }
 *
 * // Final merged classes
 * @InternalMergedTypeMarker(
 *   originClass = UserSubcomponent_12c9cde5::class,
 *   scope = UserScope::class,
 * )
 * @Subcomponent(
 *   modules = [
 *     UserSubcomponent_12c9cde5.BindingModule::class,
 *     MergedUserSubcomponent_12c9cde5.BindingModule::class
 *   ]
 * )
 * public interface MergedUserSubcomponent_12c9cde5 : UserSubcomponent_12c9cde5 {
 *   @Subcomponent.Factory
 *   public interface ComponentFactory : UserSubcomponent.ComponentFactory
 *
 *   @Module
 *   public abstract class SubcomponentModule {
 *     @Binds
 *     public abstract fun bindSubcomponentFactory(
 *       factory: ComponentFactory
 *     ): UserSubcomponent.ComponentFactory
 *   }
 *
 *   public interface ParentComponent {
 *     public fun createComponentFactory(): ComponentFactory
 *   }
 *
 *   @Module
 *   public interface BindingModule {
 *     @Binds
 *     public fun bindMergedUserSubcomponent_12c9cde5(
 *       impl: MergedUserSubcomponent_12c9cde5
 *     ): UserSubcomponent_12c9cde5
 *
 *     @Binds
 *     public fun bindComponentFactory(impl: ComponentFactory): UserSubcomponent.ComponentFactory
 *   }
 * }
 * ```
 *
 * ## Contributed Parent Component
 *
 * ```kotlin
 * @ContributesSubcomponent(UserScope::class, parentScope = AppScope::class)
 * interface UserSubcomponent {
 *   @ContributesTo(AppScope::class)
 *   interface UserScopeParentComponent {
 *     fun createComponent(): UserSubcomponent
 *   }
 * }
 * ```
 *
 * In this case, [contributor] will be set to the `UserScopeParentComponent` interface. This in turn
 * will generate code like so.
 *
 * ```kotlin
 * // Intermediate merge class
 * @MergeSubcomponent(scope = UserScope::class)
 * @InternalContributedSubcomponentMarker(
 *   originClass = UserSubcomponent::class,
 *   contributor = UserSubcomponent.UserScopeParentComponent::class,
 * )
 * public interface UserSubcomponent_12c9cde5 : UserSubcomponent {
 *   @Module
 *   public interface BindingModule {
 *     @Binds
 *     public fun bindUserSubcomponent_12c9cde5(impl: UserSubcomponent_12c9cde5): UserSubcomponent
 *   }
 * }
 *
 * // Final merged classes
 * @InternalMergedTypeMarker(
 *   originClass = UserSubcomponent_12c9cde5::class,
 *   scope = UserScope::class,
 * )
 * @Subcomponent(
 *   modules = [
 *     UserSubcomponent_12c9cde5.BindingModule::class,
 *     MergedUserSubcomponent_12c9cde5.BindingModule::class
 *   ]
 * )
 * public interface MergedUserSubcomponent_12c9cde5 : UserSubcomponent_12c9cde5 {
 *   @Module
 *   public interface BindingModule {
 *     @Binds
 *     public fun bindMergedUserSubcomponent_12c9cde5(
 *       impl: MergedUserSubcomponent_12c9cde5
 *     ): UserSubcomponent_12c9cde5
 *   }
 *
 *   public interface ParentComponent : UserSubcomponent.UserScopeParentComponent {
 *     override fun createComponent(): MergedUserSubcomponent_12c9cde5
 *   }
 * }
 * ```
 *
 * ## No Contributed Parent Component or Factory
 *
 * ```kotlin
 * @ContributesSubcomponent(UserScope::class, parentScope = AppScope::class)
 * interface UserSubcomponent
 * ```
 *
 * In this case, [contributor] will be set to a generated `ParentComponent` interface in the
 * generated mergeable subcomponent. This in turn will generate code like so.
 *
 * ```kotlin
 * // Intermediate merge class
 * @MergeSubcomponent(scope = UserScope::class)
 * @InternalContributedSubcomponentMarker(
 *   originClass = UserSubcomponent::class,
 *   contributor = UserSubcomponent_12c9cde5.ParentComponent::class,
 * )
 * public interface UserSubcomponent_12c9cde5 : UserSubcomponent {
 *   @Module
 *   public interface BindingModule {
 *     @Binds
 *     public fun bindUserSubcomponent_12c9cde5(impl: UserSubcomponent_12c9cde5): UserSubcomponent
 *   }
 *
 *   public interface ParentComponent {
 *     public fun createUserSubcomponent(): UserSubcomponent
 *   }
 * }
 *
 * // Final merged classes
 * @InternalMergedTypeMarker(
 *   originClass = UserSubcomponent_12c9cde5::class,
 *   scope = UserScope::class,
 * )
 * @Subcomponent(
 *   modules = [
 *     UserSubcomponent_12c9cde5.BindingModule::class,
 *     MergedUserSubcomponent_12c9cde5.BindingModule::class
 *   ]
 * )
 * public interface MergedUserSubcomponent_12c9cde5 : UserSubcomponent_12c9cde5 {
 *   @Module
 *   public interface BindingModule {
 *     @Binds
 *     public fun bindMergedUserSubcomponent_12c9cde5(
 *       impl: MergedUserSubcomponent_12c9cde5
 *     ): UserSubcomponent_12c9cde5
 *   }
 *
 *   public interface ParentComponent : UserSubcomponent_12c9cde5.ParentComponent {
 *     public override fun createUserSubcomponent(): MergedUserSubcomponent_12c9cde5
 *   }
 * }
 * ```
 */
@Target(AnnotationTarget.CLASS)
public annotation class InternalContributedSubcomponentMarker(
  val originClass: KClass<*>,
  val contributor: KClass<*> = Nothing::class,
  val componentFactory: KClass<*> = Nothing::class,
)
