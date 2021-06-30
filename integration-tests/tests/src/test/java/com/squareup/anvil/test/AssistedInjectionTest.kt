package com.squareup.anvil.test

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.annotations.MergeComponent
import dagger.Module
import dagger.Provides
import dagger.assisted.AssistedFactory
import org.junit.Test

internal class AssistedInjectionTest {

  @Test fun `assisted injection code is generated`() {
    val assistedService = DaggerAssistedInjectionTest_AppComponent.create()
      .serviceFactory()
      .create("Hello")

    assertThat(assistedService.string).isEqualTo("Hello")
  }

  @Test fun `a factory can be declared in a different module`() {
    val assistedService = DaggerAssistedInjectionTest_AppComponent.create()
      .serviceFactory2()
      .create("Hello")

    assertThat(assistedService.string).isEqualTo("Hello")
  }

  @MergeComponent(
    scope = AssistedScope::class,
    modules = [DaggerModule::class]
  )
  interface AppComponent {
    fun serviceFactory(): AssistedService.Factory
    fun serviceFactory2(): Factory2
  }

  @AssistedFactory
  interface Factory2 {
    fun create(string: String): AssistedServiceImpl
  }

  @Module
  object DaggerModule {
    @Provides fun provideInt(): Int = 5
  }
}
