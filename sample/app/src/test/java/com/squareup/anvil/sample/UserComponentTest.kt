package com.squareup.anvil.sample

import org.junit.Test

class UserComponentTest {

  @Test fun `UserComponent is generated`() {
    val parent = DaggerAppComponent.create() as UserComponent.Parent
    val baseComponent = parent.user().create()
    val userComponent = baseComponent as UserComponent

    assert(baseComponent.description() == UserDescriptionModule.provideDescription())
    assert(userComponent.username() == UserDescriptionModule.provideName())
  }
}
