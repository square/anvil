package com.squareup.anvil.test

import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
@ContributesTo(AppScope::class)
public abstract class AppModule1

@Module
@ContributesTo(AppScope::class)
public object AppModule2 {
  @Provides @Singleton
  public fun provideFunction(): (String) -> Int = { it.length }

  @Provides internal fun provideType(): CharSequence = "Hello"
}

@Module
@ContributesTo(SubScope::class)
public class SubModule1(private val string: String) {
  @Provides
  public fun provideInput(): String = string
}

@Module
@ContributesTo(SubScope::class)
public interface SubModule2
