package com.squareup.anvil.test

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.annotations.compat.MergeModules
import dagger.Component
import dagger.Module
import dagger.Subcomponent
import org.junit.Test
import javax.inject.Named
import javax.inject.Singleton

internal class MergeModulesTest {

  @Test fun `contributed modules are merged app scope`() {
    val annotation = CompositeAppModule::class.java.getAnnotation(Module::class.java)!!
    assertThat(annotation.includes.withoutAnvilModule())
      .containsExactly(AppModule1::class, AppModule2::class)
    assertThat(annotation.includes.toList()).doesNotContain(SubModule1::class)
    assertThat(annotation.includes.toList()).doesNotContain(SubModule2::class)
  }

  @Test fun `contributed modules are merge sub scope`() {
    val annotation = CompositeSubModule::class.java.getAnnotation(Module::class.java)!!
    assertThat(annotation.includes.withoutAnvilModule())
      .containsExactly(SubModule1::class, SubModule2::class)
    assertThat(annotation.includes.toList()).doesNotContain(AppModule1::class)
    assertThat(annotation.includes.toList()).doesNotContain(AppModule2::class)
  }

  @Test fun `contributed bindings bind types for each scope`() {
    val appComponent = DaggerMergeModulesTest_AppComponent.create()
    val subComponent = appComponent.subComponent()

    assertThat(appComponent.parentType()).isInstanceOf(AppBinding::class.java)
    assertThat(subComponent.parentType()).isInstanceOf(AppBinding::class.java)
    assertThat(subComponent.middleType()).isInstanceOf(SubcomponentBinding::class.java)
  }

  @MergeModules(AppScope::class)
  abstract class CompositeAppModule

  @MergeModules(SubScope::class)
  abstract class CompositeSubModule

  @Component(modules = [CompositeAppModule::class])
  @Singleton
  interface AppComponent {
    fun subComponent(): SubComponent
    fun parentType(): ParentType
  }

  @Subcomponent(modules = [CompositeSubModule::class])
  interface SubComponent {
    @Named("middle") fun middleType(): MiddleType
    fun parentType(): ParentType
  }
}
