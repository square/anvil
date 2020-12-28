package com.squareup.anvil.sample

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LoggedInComponentTest {

  @Test fun `LoggedInComponent can be created`() {
    val loggedInComponent = DaggerAppComponent.create()
      .loggedInComponent()

    assertThat(loggedInComponent).isNotNull()
  }
}
