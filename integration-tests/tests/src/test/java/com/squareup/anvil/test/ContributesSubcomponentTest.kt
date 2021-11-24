package com.squareup.anvil.test

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.annotations.MergeComponent
import org.junit.Test

class ContributesSubcomponentTest {

  @Test fun `a subcomponent is contributed`() {
    val appComponent =
      DaggerContributesSubcomponentTest_AppComponent.create()
        as ContributedSubcomponent.ParentInterface

    assertThat(appComponent.component().integer()).isEqualTo(3)
  }

  @Test fun `a subcomponent is contributed with a factory`() {
    val appComponent =
      DaggerContributesSubcomponentTest_AppComponentFactory.create()
        as ContributedSubcomponentFactory.ParentInterface

    assertThat(appComponent.factory().createComponent(3).integer()).isEqualTo(3)
  }

  @MergeComponent(ContributedSubcomponent.ParentScope::class)
  interface AppComponent

  @MergeComponent(ContributedSubcomponentFactory.ParentScope::class)
  interface AppComponentFactory
}
