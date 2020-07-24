package com.squareup.anvil.sample

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RandomModuleTest {
  @Test fun `module in this app module is contributed`() {
    val component = DaggerAppComponent.create() as RandomComponent

    assertThat(component.string()).isEqualTo(RandomModule.provideString())
  }
}
