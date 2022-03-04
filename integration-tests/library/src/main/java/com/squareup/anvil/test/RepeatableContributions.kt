package com.squareup.anvil.test

import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides

public abstract class RepeatedScope1 private constructor()
public abstract class RepeatedScope2 private constructor()

@ContributesTo(RepeatedScope1::class)
@ContributesTo(RepeatedScope2::class)
public interface RepeatedInterface {
  public fun string(): String
}

@ContributesTo(RepeatedScope1::class)
@ContributesTo(RepeatedScope2::class)
@Module
public object RepeatedModule {
  @Provides public fun provideString(): String = "Hello"
}
