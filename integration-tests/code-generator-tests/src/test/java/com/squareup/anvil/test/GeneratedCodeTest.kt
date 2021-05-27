package com.squareup.anvil.test

import com.google.common.truth.Truth.assertThat
import com.sqareup.anvil.compiler.internal.testing.extends
import com.sqareup.anvil.compiler.internal.testing.withoutAnvilModule
import com.squareup.anvil.annotations.MergeComponent
import dagger.Component
import org.junit.Test

class GeneratedCodeTest {

  @Test fun `the class is generated`() {
    val generatedClass = Class.forName("generated.test.com.squareup.anvil.test.GeneratedClass")
    assertThat(generatedClass).isNotNull()
  }

  @Test fun `the generated interface is contributed to the scope`() {
    val generatedInterface = Class
      .forName("generated.test.com.squareup.anvil.test.ContributedInterface")
      .kotlin

    assertThat(AppComponent::class extends generatedInterface).isTrue()
  }

  @Test fun `the generated module is contributed to the scope`() {
    val contributedModule =
      Class.forName("generated.test.com.squareup.anvil.test.ContributedModule").kotlin

    val annotation = AppComponent::class.java.getAnnotation(Component::class.java)!!
    assertThat(annotation.modules.withoutAnvilModule())
      .containsExactly(contributedModule)
  }

  @MergeComponent(Unit::class)
  interface AppComponent
}
