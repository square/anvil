package com.squareup.anvil.test

import com.google.common.truth.Truth.assertThat
import dagger.Component
import dagger.Module
import dagger.Provides
import org.junit.Test

internal class AssistedInjectionTest {

  @Test fun `assisted injection code is generated`() {
    val assistedService = DaggerAssistedInjectionTest_AppComponent.create()
      .serviceFactory()
      .create("Hello")

    assertThat(assistedService.string).isEqualTo("Hello")
  }

  @Component(modules = [DaggerModule::class])
  interface AppComponent {
    fun serviceFactory(): AssistedService.Factory
  }

  @Module
  object DaggerModule {
    @Provides fun provideInt(): Int = 5
  }
}
