package com.squareup.anvil.compiler.codegen

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.annotations.MergeComponent
import com.squareup.anvil.annotations.MergeSubcomponent
import com.squareup.anvil.annotations.compat.MergeModules
import com.squareup.anvil.compiler.compile
import com.squareup.anvil.compiler.componentInterfaceAnvilModule
import com.squareup.anvil.compiler.contributingInterface
import com.squareup.anvil.compiler.parentInterface
import com.squareup.anvil.compiler.secondContributingInterface
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.COMPILATION_ERROR
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import kotlin.reflect.KClass

@RunWith(Parameterized::class)
class BindingModulePriorityTest(
  annotationClass: KClass<*>
) {

  private val annotation = "@${annotationClass.simpleName}"
  private val import = "import ${annotationClass.java.canonicalName}"

  companion object {
    @Parameters(name = "{0}")
    @JvmStatic fun annotationClasses(): Collection<Any> {
      return listOf(MergeComponent::class, MergeSubcomponent::class, MergeModules::class)
    }
  }

  @Test fun `the binding with the higher priority is used`() {
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesBinding
      import com.squareup.anvil.annotations.ContributesBinding.Priority.HIGH
      import com.squareup.anvil.annotations.ContributesBinding.Priority.HIGHEST
      $import
      
      interface ParentInterface
      
      @ContributesBinding(Any::class, priority = HIGHEST)
      interface ContributingInterface : ParentInterface
      
      @ContributesBinding(Any::class, priority = HIGH)
      interface ContributingInterface2 : ParentInterface
      
      @ContributesBinding(Any::class)
      interface ContributingInterface3 : ParentInterface
      
      $annotation(Any::class)
      interface ComponentInterface
      """
    ) {
      val bindingMethod = componentInterfaceAnvilModule.declaredMethods.single()

      with(bindingMethod) {
        assertThat(returnType).isEqualTo(parentInterface)
        assertThat(parameterTypes.toList()).containsExactly(contributingInterface)
      }
    }
  }

  @Test fun `the binding with the higher priority is used with one replaced binding`() {
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesBinding
      import com.squareup.anvil.annotations.ContributesBinding.Priority.HIGH
      import com.squareup.anvil.annotations.ContributesBinding.Priority.HIGHEST
      $import
      
      interface ParentInterface
      
      @ContributesBinding(Any::class, priority = HIGHEST)
      interface ContributingInterface : ParentInterface
      
      @ContributesBinding(Any::class, priority = HIGH, replaces = [ContributingInterface::class])
      interface SecondContributingInterface : ParentInterface
      
      @ContributesBinding(Any::class)
      interface ContributingInterface3 : ParentInterface
      
      $annotation(Any::class)
      interface ComponentInterface
      """
    ) {
      val bindingMethod = componentInterfaceAnvilModule.declaredMethods.single()

      with(bindingMethod) {
        assertThat(returnType).isEqualTo(parentInterface)
        assertThat(parameterTypes.toList()).containsExactly(secondContributingInterface)
      }
    }
  }

  @Test fun `bindings with the same priority throw an error`() {
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesBinding
      $import
      
      interface ParentInterface
      
      @ContributesBinding(Any::class)
      interface ContributingInterface : ParentInterface
      
      @ContributesBinding(Any::class)
      interface SecondContributingInterface : ParentInterface
      
      $annotation(Any::class)
      interface ComponentInterface
      """
    ) {
      assertThat(exitCode).isEqualTo(COMPILATION_ERROR)

      assertThat(messages).contains(
        "There are multiple contributed bindings with the same bound type. The bound type is " +
          "com.squareup.test.ParentInterface. The contributed binding classes are: ["
      )
      // Check the contributed bindings separately, we cannot rely on the order in the string.
      assertThat(messages).contains("com.squareup.test.ContributingInterface")
      assertThat(messages).contains("com.squareup.test.SecondContributingInterface")
    }
  }

  @Test fun `bindings with the same priority can be replaced and aren't duplicate bindings`() {
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesBinding
      $import
      
      interface ParentInterface
      
      @ContributesBinding(Any::class)
      interface ContributingInterface : ParentInterface
      
      @ContributesBinding(Any::class, replaces = [ContributingInterface::class])
      interface SecondContributingInterface : ParentInterface
      
      $annotation(Any::class)
      interface ComponentInterface
      """
    ) {
      val bindingMethod = componentInterfaceAnvilModule.declaredMethods.single()

      with(bindingMethod) {
        assertThat(returnType).isEqualTo(parentInterface)
        assertThat(parameterTypes.toList()).containsExactly(secondContributingInterface)
      }
    }
  }

  @Test fun `bindings with the same priority can be excluded and aren't duplicate bindings`() {
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesBinding
      $import
      
      interface ParentInterface
      
      @ContributesBinding(Any::class)
      interface ContributingInterface : ParentInterface
      
      @ContributesBinding(Any::class)
      interface SecondContributingInterface : ParentInterface
      
      $annotation(Any::class, exclude = [ContributingInterface::class])
      interface ComponentInterface
      """
    ) {
      val bindingMethod = componentInterfaceAnvilModule.declaredMethods.single()

      with(bindingMethod) {
        assertThat(returnType).isEqualTo(parentInterface)
        assertThat(parameterTypes.toList()).containsExactly(secondContributingInterface)
      }
    }
  }

  @Test fun `bindings can use different qualifiers`() {
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesBinding
      import javax.inject.Named
      $import
      
      interface ParentInterface
      
      @ContributesBinding(Any::class)
      @Named("a")
      interface ContributingInterface : ParentInterface
      
      @ContributesBinding(Any::class)
      @Named("b")
      interface SecondContributingInterface : ParentInterface
      
      $annotation(Any::class)
      interface ComponentInterface
      """
    ) {
      val bindingMethods = componentInterfaceAnvilModule.declaredMethods
        .filter { it.returnType == parentInterface }

      assertThat(bindingMethods).hasSize(2)

      assertThat(
        bindingMethods.singleOrNull { it.parameterTypes.contains(contributingInterface) }
      ).isNotNull()
      assertThat(
        bindingMethods.singleOrNull { it.parameterTypes.contains(secondContributingInterface) }
      ).isNotNull()
    }
  }

  @Test fun `bindings with the same qualifier are duplicate bindings`() {
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesBinding
      import javax.inject.Named
      $import
      
      interface ParentInterface
      
      @ContributesBinding(Any::class)
      @Named("a")
      interface ContributingInterface : ParentInterface
      
      @ContributesBinding(Any::class)
      @Named("a")
      interface SecondContributingInterface : ParentInterface
      
      $annotation(Any::class)
      interface ComponentInterface
      """
    ) {
      assertThat(exitCode).isEqualTo(COMPILATION_ERROR)

      assertThat(messages).contains(
        "There are multiple contributed bindings with the same bound type. The bound type is " +
          "com.squareup.test.ParentInterface. The contributed binding classes are: ["
      )
      // Check the contributed bindings separately, we cannot rely on the order in the string.
      assertThat(messages).contains("com.squareup.test.ContributingInterface")
      assertThat(messages).contains("com.squareup.test.SecondContributingInterface")
    }
  }

  @Test fun `ignored qualifiers can lead to duplicate bindings`() {
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesBinding
      import javax.inject.Named
      $import
      
      interface ParentInterface
      
      @ContributesBinding(Any::class, ignoreQualifier = true)
      @Named("a")
      interface ContributingInterface : ParentInterface
      
      @ContributesBinding(Any::class, ignoreQualifier = true)
      @Named("b")
      interface SecondContributingInterface : ParentInterface
      
      $annotation(Any::class)
      interface ComponentInterface
      """
    ) {
      assertThat(exitCode).isEqualTo(COMPILATION_ERROR)

      assertThat(messages).contains(
        "There are multiple contributed bindings with the same bound type. The bound type is " +
          "com.squareup.test.ParentInterface. The contributed binding classes are: ["
      )
      // Check the contributed bindings separately, we cannot rely on the order in the string.
      assertThat(messages).contains("com.squareup.test.ContributingInterface")
      assertThat(messages).contains("com.squareup.test.SecondContributingInterface")
    }
  }

  @Test fun `bindings with the same qualifier can be excluded`() {
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesBinding
      import javax.inject.Named
      $import
      
      interface ParentInterface
      
      @ContributesBinding(Any::class)
      @Named("a")
      interface ContributingInterface : ParentInterface
      
      @ContributesBinding(Any::class)
      @Named("a")
      interface SecondContributingInterface : ParentInterface
      
      $annotation(Any::class, exclude = [ContributingInterface::class])
      interface ComponentInterface
      """
    ) {
      val bindingMethod = componentInterfaceAnvilModule.declaredMethods.single()

      with(bindingMethod) {
        assertThat(returnType).isEqualTo(parentInterface)
        assertThat(parameterTypes.toList()).containsExactly(secondContributingInterface)
      }
    }
  }

  @Test fun `multiple multibindings with the same type are allowed`() {
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesMultibinding
      $import
      
      interface ParentInterface
      
      @ContributesMultibinding(Any::class)
      interface ContributingInterface : ParentInterface
      
      @ContributesMultibinding(Any::class)
      interface SecondContributingInterface : ParentInterface
      
      $annotation(Any::class)
      interface ComponentInterface
      """
    ) {
      val bindingMethods = componentInterfaceAnvilModule.declaredMethods
      assertThat(bindingMethods).hasLength(2)
    }
  }
}
