package com.squareup.anvil.sample

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

class RandomModuleTest {
  @Test
  fun `module in this app module is contributed`() {
    val component = DaggerAppComponent.create() as RandomComponent

    // Verify
    assertWithMessage("Module should provide a non-empty string").that(component.string())
      .isNotEmpty()
    assertWithMessage("Module should provide a deterministic string").that(component.string())
      .isEqualTo(component.string())
  }
}
