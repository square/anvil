package com.squareup.hephaestus.test

import com.google.common.truth.Truth.assertThat
import com.squareup.hephaestus.annotations.MergeComponent
import com.squareup.hephaestus.annotations.MergeSubcomponent
import dagger.Component
import dagger.Subcomponent
import org.junit.Test

class MergeComponentTest {

  @Test fun `component merges modules and interfaces`() {
    val annotation = AppComponent::class.java.getAnnotation(Component::class.java)!!
    assertThat(annotation.modules.toList()).containsExactly(AppModule1::class, AppModule2::class)

    assertThat(AppComponent::class extends AppComponentInterface::class).isTrue()
    assertThat(AppComponent::class extends AppModule2::class).isFalse()
    assertThat(AppComponent::class extends SubModule2::class).isFalse()
  }

  @Test fun `subcomponent merges modules and interfaces`() {
    val annotation = SubComponent::class.java.getAnnotation(Subcomponent::class.java)!!
    assertThat(annotation.modules.toList()).containsExactly(SubModule1::class, SubModule2::class)

    assertThat(SubComponent::class extends SubComponentInterface::class).isTrue()
    assertThat(AppComponent::class extends AppModule2::class).isFalse()
    assertThat(AppComponent::class extends SubModule2::class).isFalse()
  }

  @MergeComponent(AppScope::class)
  interface AppComponent {
    fun subComponent(): SubComponent
  }

  @MergeSubcomponent(SubScope::class)
  interface SubComponent
}
