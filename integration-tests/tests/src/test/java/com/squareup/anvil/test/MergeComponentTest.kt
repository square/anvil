package com.squareup.anvil.test

import com.google.common.truth.Truth
import com.squareup.anvil.annotations.MergeComponent
import com.squareup.anvil.annotations.MergeSubcomponent
import dagger.Component
import dagger.Subcomponent
import org.junit.Test

class MergeComponentTest {

  @Test fun `component merges modules and interfaces`() {
    val annotation = AppComponent::class.java.getAnnotation(Component::class.java)!!
    Truth.assertThat(annotation.modules.toList())
        .containsExactly(AppModule1::class, AppModule2::class)

    Truth.assertThat(AppComponent::class extends AppComponentInterface::class)
        .isTrue()
    Truth.assertThat(AppComponent::class extends AppModule2::class)
        .isFalse()
    Truth.assertThat(AppComponent::class extends SubModule2::class)
        .isFalse()
  }

  @Test fun `subcomponent merges modules and interfaces`() {
    val annotation = SubComponent::class.java.getAnnotation(Subcomponent::class.java)!!
    Truth.assertThat(annotation.modules.toList())
        .containsExactly(SubModule1::class, SubModule2::class)

    Truth.assertThat(SubComponent::class extends SubComponentInterface::class)
        .isTrue()
    Truth.assertThat(AppComponent::class extends AppModule2::class)
        .isFalse()
    Truth.assertThat(AppComponent::class extends SubModule2::class)
        .isFalse()
  }

  @MergeComponent(AppScope::class)
  interface AppComponent {
    fun subComponent(): SubComponent
  }

  @MergeSubcomponent(SubScope::class)
  interface SubComponent
}
