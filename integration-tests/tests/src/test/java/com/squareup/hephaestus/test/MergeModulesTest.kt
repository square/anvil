package com.squareup.hephaestus.test

import com.google.common.truth.Truth.assertThat
import com.squareup.hephaestus.annotations.compat.MergeModules
import dagger.Module
import org.junit.Test

class MergeModulesTest {

  @Test fun `contributed modules are merged app scope`() {
    val annotation = CompositeAppModule::class.java.getAnnotation(Module::class.java)!!
    assertThat(annotation.includes.toList()).contains(AppModule::class)
    assertThat(annotation.includes.toList()).doesNotContain(SubModule::class)
  }

  @Test fun `contributed modules are merge sub scope`() {
    val annotation = CompositeSubModule::class.java.getAnnotation(Module::class.java)!!
    assertThat(annotation.includes.toList()).contains(SubModule::class)
    assertThat(annotation.includes.toList()).doesNotContain(AppModule::class)
  }

  @MergeModules(AppScope::class)
  abstract class CompositeAppModule

  @MergeModules(SubScope::class)
  abstract class CompositeSubModule
}
