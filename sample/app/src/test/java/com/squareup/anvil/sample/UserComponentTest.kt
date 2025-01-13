package com.squareup.anvil.sample

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UserComponentTest {

  @Test fun `UserComponent is generated`() {
    val parent = DaggerAppComponent.create() as UserComponent.Parent
    val baseComponent = parent.user().create()
    val userComponent = baseComponent as UserComponent

    assertThat(baseComponent.description()).isEqualTo(UserDescriptionModule.provideDescription())
    assertThat(userComponent.username()).isEqualTo(UserDescriptionModule.provideName())
  }
}
