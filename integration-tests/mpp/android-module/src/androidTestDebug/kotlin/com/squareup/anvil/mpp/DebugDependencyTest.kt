package com.squareup.anvil.mpp

import com.google.common.truth.Truth.assertThat
import org.junit.Test

@Suppress("IllegalIdentifier")
class DebugDependencyTest {

  @Test fun `can create component from main source set`() {
    assertThat(DaggerComponent.create().dependency()).isSameInstanceAs(DependencyImpl)
  }

  @Test fun `can create component from debug source set`() {
    assertThat(DaggerDebugComponent.create().debugDependency())
      .isSameInstanceAs(DebugDependencyImpl)
  }
}
