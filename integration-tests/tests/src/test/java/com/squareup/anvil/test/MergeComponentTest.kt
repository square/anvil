package com.squareup.anvil.test

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.annotations.MergeComponent
import com.squareup.anvil.annotations.MergeSubcomponent
import dagger.Component
import dagger.Subcomponent
import org.junit.Test
import javax.inject.Singleton

class MergeComponentTest {

  @Test fun `component merges modules and interfaces`() {
    val annotation = AppComponent::class.java.getAnnotation(Component::class.java)!!
    assertThat(annotation.modules.withoutAnvilModule())
        .containsExactly(AppModule1::class, AppModule2::class)

    assertThat(AppComponent::class extends AppComponentInterface::class).isTrue()
    assertThat(AppComponent::class extends AppModule2::class).isFalse()
    assertThat(AppComponent::class extends SubModule2::class).isFalse()
  }

  @Test fun `subcomponent merges modules and interfaces`() {
    val annotation = SubComponent::class.java.getAnnotation(Subcomponent::class.java)!!
    assertThat(annotation.modules.withoutAnvilModule())
        .containsExactly(SubModule1::class, SubModule2::class)

    assertThat(SubComponent::class extends SubComponentInterface::class).isTrue()
    assertThat(AppComponent::class extends AppModule2::class).isFalse()
    assertThat(AppComponent::class extends SubModule2::class).isFalse()
  }

  @Test fun `contributed bindings bind types for each scope`() {
    val appComponent = DaggerMergeComponentTest_AppComponent.create()
    val subComponent = appComponent.subComponent()

    assertThat(appComponent.parentType()).isInstanceOf(AppBinding::class.java)
    assertThat(subComponent.parentType()).isInstanceOf(AppBinding::class.java)
    assertThat(subComponent.middleType()).isInstanceOf(SubcomponentBinding::class.java)
  }

  @MergeComponent(AppScope::class)
  @Singleton
  interface AppComponent {
    fun subComponent(): SubComponent
    fun parentType(): ParentType
    fun function(): (String) -> Int
  }

  @MergeSubcomponent(SubScope::class)
  interface SubComponent {
    fun middleType(): MiddleType
    fun parentType(): ParentType
  }
}
