package com.squareup.anvil.test

import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
@ContributesTo(AppScope::class)
abstract class AppModule1

@Module
@ContributesTo(AppScope::class)
object AppModule2 {
  @Provides @Singleton fun provideFunction(): (String) -> Int = { it.length }
}

@Module
@ContributesTo(SubScope::class)
class SubModule1(private val string: String) {
  @Provides fun provideInput(): String = string
}

@Module
@ContributesTo(SubScope::class)
interface SubModule2
