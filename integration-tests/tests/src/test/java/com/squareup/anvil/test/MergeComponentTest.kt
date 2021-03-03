package com.squareup.anvil.test

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.annotations.MergeComponent
import com.squareup.anvil.annotations.MergeSubcomponent
import dagger.Component
import dagger.Subcomponent
import org.junit.Test
import javax.inject.Named
import javax.inject.Singleton

internal class MergeComponentTest {

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
    assertThat(subComponent.middleType()).isInstanceOf(SubcomponentBinding1::class.java)
  }

  @Test fun `contributed multibindings bind types for each scope`() {
    val appComponent = DaggerMergeComponentTest_AppComponent.create()
    val subComponent = appComponent.subComponent()

    assertThat(appComponent.parentTypes()).hasSize(1)
    assertThat(subComponent.middleTypes())
      .containsExactly(SubcomponentBinding1, SubcomponentBinding3)
    assertThat(subComponent.middleTypesNamed()).containsExactly(SubcomponentBinding2)
  }

  @Test fun `contributed map multibindings are provided`() {
    val appComponent = DaggerMergeComponentTest_AppComponent.create()

    assertThat(appComponent.mapBindings()).containsExactly("1", MapBinding1, "3", MapBinding3)
    assertThat(appComponent.mapBindingsNamed()).containsExactly("2", MapBinding2)
  }

  @MergeComponent(AppScope::class)
  @Singleton
  @Suppress("unused")
  interface AppComponent {
    fun subComponent(): SubComponent
    fun parentType(): ParentType
    fun function(): (String) -> Int
    fun charSequence(): CharSequence
    fun parentTypes(): Set<ParentType>
    fun mapBindings(): Map<String, ParentType>
    @Named("abc") fun mapBindingsNamed(): Map<String, ParentType>
  }

  @MergeSubcomponent(SubScope::class)
  interface SubComponent {
    @Named("middle") fun middleType(): MiddleType
    fun parentType(): ParentType

    fun middleTypes(): Set<MiddleType>
    @Named("middle") fun middleTypesNamed(): Set<MiddleType>
  }
}
