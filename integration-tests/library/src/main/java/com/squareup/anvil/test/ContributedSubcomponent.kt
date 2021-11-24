@file:Suppress("RemoveRedundantQualifierName")

package com.squareup.anvil.test

import com.squareup.anvil.annotations.ContributesSubcomponent
import com.squareup.anvil.annotations.ContributesTo
import dagger.BindsInstance
import dagger.Module
import dagger.Provides

@ContributesSubcomponent(
  scope = ContributedSubcomponent.Scope::class,
  parentScope = ContributedSubcomponent.ParentScope::class
)
public interface ContributedSubcomponent {
  public fun integer(): Int

  @ContributesTo(ContributedSubcomponent.ParentScope::class)
  public interface ParentInterface {
    public fun component(): ContributedSubcomponent
  }

  @ContributesTo(ContributedSubcomponent.Scope::class)
  @Module
  public object SubcomponentModule {
    @Provides public fun provideInteger(): Int = 3
  }

  public abstract class Scope
  public abstract class ParentScope
}

@ContributesSubcomponent(
  scope = ContributedSubcomponentFactory.Scope::class,
  parentScope = ContributedSubcomponentFactory.ParentScope::class
)
public interface ContributedSubcomponentFactory {
  public fun integer(): Int

  @ContributesTo(ContributedSubcomponentFactory.ParentScope::class)
  public interface ParentInterface {
    public fun factory(): Factory
  }

  @ContributesSubcomponent.Factory
  public interface Factory {
    public fun createComponent(
      @BindsInstance integer: Int
    ): ContributedSubcomponentFactory
  }

  public abstract class Scope
  public abstract class ParentScope
}
