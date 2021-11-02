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

  @MergeComponent(ContributesSubcomponentParentScope::class)
  interface AppComponent
}
