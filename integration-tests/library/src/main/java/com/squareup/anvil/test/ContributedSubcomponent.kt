package com.squareup.anvil.test

import com.squareup.anvil.annotations.ContributesSubcomponent
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides

public object ContributesSubcomponentScope
public object ContributesSubcomponentParentScope

@ContributesSubcomponent(
  scope = ContributesSubcomponentScope::class,
  parentScope = ContributesSubcomponentParentScope::class
)
public interface ContributedSubcomponent {
  public fun integer(): Int

  @ContributesTo(ContributesSubcomponentParentScope::class)
  public interface ParentInterface {
    public fun component(): ContributedSubcomponent
  }

  @ContributesTo(ContributesSubcomponentScope::class)
  @Module
  public object SubcomponentModule {
    @Provides public fun provideInteger(): Int = 3
  }
}
