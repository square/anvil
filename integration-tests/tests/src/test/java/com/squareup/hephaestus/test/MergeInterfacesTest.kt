package com.squareup.hephaestus.test

import com.google.common.truth.Truth.assertThat
import com.squareup.hephaestus.annotations.compat.MergeInterfaces
import org.junit.Test

class MergeInterfacesTest {

  @Test fun `contributed interfaces are merged`() {
    assertThat(CompositeAppComponent::class extends AppComponentInterface::class).isTrue()
    assertThat(CompositeAppComponent::class extends SubComponentInterface::class).isFalse()

    assertThat(CompositeSubComponent::class extends SubComponentInterface::class).isTrue()
    assertThat(CompositeSubComponent::class extends AppComponentInterface::class).isFalse()
  }

  @MergeInterfaces(AppScope::class)
  interface CompositeAppComponent

  @MergeInterfaces(SubScope::class)
  interface CompositeSubComponent
}
