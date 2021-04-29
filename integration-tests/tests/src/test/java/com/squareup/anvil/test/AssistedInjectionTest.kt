package com.squareup.anvil.test

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.annotations.MergeComponent
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

  @MergeComponent(
    scope = AssistedScope::class,
    modules = [DaggerModule::class]
  )
  interface AppComponent {
    fun serviceFactory(): AssistedService.Factory
  }

  @Module
  object DaggerModule {
    @Provides fun provideInt(): Int = 5
  }
}
