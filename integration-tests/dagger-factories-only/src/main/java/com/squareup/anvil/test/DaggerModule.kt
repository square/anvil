package com.squareup.anvil.test

import dagger.Module
import dagger.Provides

@Module
@Suppress("unused")
object DaggerModule {
  @Provides fun provideString(): String = "Hello Anvil"
}
