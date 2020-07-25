package com.squareup.anvil.test

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.annotations.compat.MergeModules
import dagger.Module
import org.junit.Test

class MergeModulesTest {

  @Test fun `contributed modules are merged app scope`() {
    val annotation = CompositeAppModule::class.java.getAnnotation(Module::class.java)!!
    assertThat(annotation.includes.toList()).containsExactly(AppModule1::class, AppModule2::class)
    assertThat(annotation.includes.toList()).doesNotContain(SubModule1::class)
    assertThat(annotation.includes.toList()).doesNotContain(SubModule2::class)
  }

  @Test fun `contributed modules are merge sub scope`() {
    val annotation = CompositeSubModule::class.java.getAnnotation(Module::class.java)!!
    assertThat(annotation.includes.toList()).containsExactly(SubModule1::class, SubModule2::class)
    assertThat(annotation.includes.toList()).doesNotContain(AppModule1::class)
    assertThat(annotation.includes.toList()).doesNotContain(AppModule2::class)
  }

  @MergeModules(AppScope::class)
  abstract class CompositeAppModule

  @MergeModules(SubScope::class)
  abstract class CompositeSubModule
}
