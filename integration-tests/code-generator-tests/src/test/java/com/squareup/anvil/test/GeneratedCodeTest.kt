package com.squareup.anvil.test

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.annotations.MergeComponent
import com.squareup.anvil.compiler.internal.testing.extends
import dagger.Component
import org.junit.Test
import javax.inject.Inject

class GeneratedCodeTest {

  @Test fun `the class is generated`() {
    val generatedClass = Class.forName("generated.test.com.squareup.anvil.test.GeneratedClass")
    assertThat(generatedClass).isNotNull()
  }

  @Test fun `the generated component interface is turned into a component`() {
    val generatedInterface = Class
      .forName("generated.test.com.squareup.anvil.test.MergedComponent")
      .kotlin

    val generatedComponentCreator = Class
      .forName("generated.test.com.squareup.anvil.test.DaggerMergedComponent")
      .methods
      .find { it.name == "create" && it.returnType.kotlin == generatedInterface }

    assertThat(generatedComponentCreator).isNotNull()
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
    val contributedBindingModule =
      Class.forName(
        "generated.test.com.squareup.anvil.test.ContributedBinding_Binding_Unit_BindingModule_4e0aa394",
      ).kotlin

    val annotation = AppComponent::class.java.getAnnotation(Component::class.java)!!
    assertThat(annotation.modules.toList())
      .containsExactly(contributedModule, contributedBindingModule)
  }

  @Test fun `the generated contributed binding can be injected`() {
    // This is a reproducer for https://github.com/square/anvil/issues/310.
    assertThat(DaggerGeneratedCodeTest_AppComponent.create().contributedBindingClass()).isNotNull()
  }

  @Test fun `the generated AssistedInject factory can be injected`() {
    // This is a reproducer for https://github.com/square/anvil/issues/326.
    assertThat(DaggerGeneratedCodeTest_AppComponent.create().assistedClass()).isNotNull()
  }

  @Test fun `the generated class can be injected`() {
    // This is a reproducer for https://github.com/square/anvil/issues/283.
    assertThat(DaggerGeneratedCodeTest_AppComponent.create().otherClass()).isNotNull()
  }

  @Test fun `the generated subcomponent is contribute the parent scope`() {
    val contributedSubcomponent =
      Class.forName("generated.test.com.squareup.anvil.test.ContributedSubcomponent")

    val component = DaggerGeneratedCodeTest_AppComponent.create()

    val subcomponent = AppComponent::class.java
      .methods
      .single { it.returnType extends contributedSubcomponent }
      .invoke(component)

    val int = subcomponent::class.java
      .declaredMethods
      .single { it.name == "integer" }
      .invoke(subcomponent) as Int

    assertThat(int).isEqualTo(7)
  }

  @Test fun `the function class is generated`() {
    val generatedClass =
      Class.forName("generated.test.com.squareup.anvil.test.GeneratedFunctionClass")
    assertThat(generatedClass).isNotNull()
  }

  @Test fun `the property class is generated`() {
    val generatedClass =
      Class.forName("generated.test.com.squareup.anvil.test.GeneratedPropertyClass")
    assertThat(generatedClass).isNotNull()
  }

  @MergeComponent(Unit::class)
  interface AppComponent {
    fun otherClass(): OtherClass
    fun assistedClass(): AssistedClass
    fun contributedBindingClass(): ContributedBindingClass
  }

  class ContributedBindingClass @Inject constructor(
    // Keep the fully qualified name, otherwise the one specific error from
    // https://github.com/square/anvil/issues/310 can't be reproduced.
    val binding: generated.test.com.squareup.anvil.test.Binding,
  )

  class AssistedClass @Inject constructor(
    // Keep the fully qualified name, otherwise the one specific error from
    // https://github.com/square/anvil/issues/326 can't be reproduced.
    val sampleAssistedFactory: generated.test.com.squareup.anvil.test.SampleAssistedFactory,
  )

  class OtherClass @Inject constructor(
    // Keep the fully qualified name, otherwise the one specific error from
    // https://github.com/square/anvil/issues/283 can't be reproduced.
    val injectClass: generated.test.com.squareup.anvil.test.InjectClass,
  )
}
