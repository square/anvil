package com.squareup.anvil.mpp

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DependencyTest {

  @Test fun `can create component`() {
    assertThat(DaggerComponent.create().dependency()).isSameInstanceAs(DependencyImpl)
  }
}
