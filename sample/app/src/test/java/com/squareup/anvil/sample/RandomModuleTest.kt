package com.squareup.anvil.sample

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.squareup.scopes.ComponentHolder
import org.junit.Test

class RandomModuleTest {
  @Test fun `module in this app module is contributed`() {
    ComponentHolder.components.add(DaggerAppComponent.create())

    val component = ComponentHolder.component<RandomComponent>()

    // Verify
    assertWithMessage("Module should provide a non-empty string").that(component.string()).isNotEmpty()
    assertWithMessage("Module should provide a deterministic string").that(component.string()).isEqualTo(component.string())
  }
}
