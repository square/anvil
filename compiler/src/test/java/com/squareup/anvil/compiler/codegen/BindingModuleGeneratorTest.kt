package com.squareup.anvil.compiler.codegen

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.annotations.MergeComponent
import com.squareup.anvil.annotations.MergeSubcomponent
import com.squareup.anvil.annotations.compat.MergeModules
import com.squareup.anvil.compiler.anvilModule
import com.squareup.anvil.compiler.compile
import com.squareup.anvil.compiler.componentInterface
import com.squareup.anvil.compiler.contributingInterface
import com.squareup.anvil.compiler.daggerModule1
import com.squareup.anvil.compiler.internal.testing.AnyDaggerComponent
import com.squareup.anvil.compiler.internal.testing.anyDaggerComponent
import com.squareup.anvil.compiler.internal.testing.daggerModule
import com.squareup.anvil.compiler.internal.testing.isAbstract
import com.squareup.anvil.compiler.parentInterface
import com.squareup.anvil.compiler.secondContributingInterface
import com.squareup.anvil.compiler.subcomponentInterface
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.COMPILATION_ERROR
import dagger.Binds
import dagger.Provides
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import kotlin.reflect.KClass

@RunWith(Parameterized::class)
class BindingModuleGeneratorTest(
  private val annotationClass: KClass<*>
) {

  private val annotation = "@${annotationClass.simpleName}"
  private val import = "import ${annotationClass.java.canonicalName}"

  companion object {
    @Parameters(name = "{0}")
    @JvmStatic fun annotationClasses(): Collection<Any> {
      return listOf(MergeComponent::class, MergeSubcomponent::class, MergeModules::class)
    }
  }

  @Test fun `a Dagger module is generated for a merged class and added to the component`() {
    compile(
      """
      package com.squareup.test
      
      $import
      
      $annotation(Any::class)
      interface ComponentInterface
      """
    ) {
      val modules = if (annotationClass == MergeModules::class) {
        componentInterface.daggerModule.includes.toList()
      } else {
        componentInterface.anyDaggerComponent.modules
      }
      assertThat(modules).containsExactly(componentInterface.anvilModule.kotlin)
    }
  }

  @Test fun `the Anvil module is merged with other modules`() {
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesTo
      $import
      
      @ContributesTo(Any::class)
      @dagger.Module
      abstract class DaggerModule1
      
      $annotation(Any::class)
      interface ComponentInterface
      """
    ) {
      val modules = if (annotationClass == MergeModules::class) {
        componentInterface.daggerModule.includes.toList()
      } else {
        componentInterface.anyDaggerComponent.modules
      }
      assertThat(modules)
        .containsExactly(componentInterface.anvilModule.kotlin, daggerModule1.kotlin)
    }
  }

  @Test fun `there is an Anvil module for each component`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesTo
      $import

      $annotation(Any::class)
      interface ComponentInterface

      $annotation(Unit::class)
      interface SubcomponentInterface
      """
    ) {
      if (annotationClass == MergeModules::class) {
        assertThat(componentInterface.daggerModule.includes.toList())
          .containsExactly(componentInterface.anvilModule.kotlin)
        assertThat(subcomponentInterface.daggerModule.includes.toList())
          .containsExactly(subcomponentInterface.anvilModule.kotlin)
      } else {
        assertThat(componentInterface.anyDaggerComponent.modules)
          .containsExactly(componentInterface.anvilModule.kotlin)
        assertThat(subcomponentInterface.anyDaggerComponent.modules)
          .containsExactly(subcomponentInterface.anvilModule.kotlin)
      }
    }
  }

  @Test fun `Anvil modules for inner classes are generated`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesTo
      $import
      
      class SomeClass {
        $annotation(Any::class)
        interface ComponentInterface
      }
      """
    ) {
      assertThat(
        classLoader.loadClass("com.squareup.test.SomeClass\$ComponentInterface").anvilModule
      ).isNotNull()
    }
  }

  @Test fun `the Dagger binding method is generated for non-objects`() {
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesBinding
      $import

      interface ParentInterface
      
      interface Middle : ParentInterface

      @ContributesBinding(Any::class, ParentInterface::class)
      interface ContributingInterface : Middle
      
      $annotation(Any::class)
      interface ComponentInterface
      """
    ) {
      val modules = if (annotationClass == MergeModules::class) {
        componentInterface.daggerModule.includes.toList()
      } else {
        componentInterface.anyDaggerComponent.modules
      }
      assertThat(modules).containsExactly(componentInterface.anvilModule.kotlin)

      val methods = modules.first().java.declaredMethods
      assertThat(methods).hasLength(1)

      with(methods[0]) {
        assertThat(returnType).isEqualTo(parentInterface)
        assertThat(parameterTypes.toList()).containsExactly(contributingInterface)
        assertThat(isAbstract).isTrue()
        assertThat(isAnnotationPresent(Binds::class.java)).isTrue()
      }
    }
  }

  @Test fun `the Dagger provider method is generated for objects`() {
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesBinding
      $import

      interface ParentInterface
      
      interface Middle : ParentInterface

      @ContributesBinding(Any::class, ParentInterface::class)
      object ContributingInterface : Middle
      
      $annotation(Any::class)
      interface ComponentInterface
      """
    ) {
      val modules = if (annotationClass == MergeModules::class) {
        componentInterface.daggerModule.includes.toList()
      } else {
        componentInterface.anyDaggerComponent.modules
      }
      assertThat(modules).containsExactly(componentInterface.anvilModule.kotlin)

      val methods = modules.first().java.declaredMethods
      assertThat(methods).hasLength(1)

      with(methods[0]) {
        assertThat(returnType).isEqualTo(parentInterface)
        assertThat(parameterTypes.toList()).isEmpty()
        assertThat(isAbstract).isFalse()
        assertThat(isAnnotationPresent(Provides::class.java)).isTrue()

        val moduleInstance = modules.first().java.declaredFields
          .first { it.name == "INSTANCE" }
          .get(null)

        assertThat(invoke(moduleInstance)::class.java.canonicalName)
          .isEqualTo("com.squareup.test.ContributingInterface")
      }
    }
  }

  @Test fun `the bound type is implied when there is only one super type`() {
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesBinding
      $import

      interface ParentInterface

      @ContributesBinding(Any::class)
      interface ContributingInterface : ParentInterface
      
      $annotation(Any::class)
      interface ComponentInterface
      """
    ) {
      val modules = if (annotationClass == MergeModules::class) {
        componentInterface.daggerModule.includes.toList()
      } else {
        componentInterface.anyDaggerComponent.modules
      }
      assertThat(modules).containsExactly(componentInterface.anvilModule.kotlin)

      val methods = modules.first().java.declaredMethods
      assertThat(methods).hasLength(1)

      with(methods[0]) {
        assertThat(returnType).isEqualTo(parentInterface)
        assertThat(parameterTypes.toList()).containsExactly(contributingInterface)
        assertThat(isAbstract).isTrue()
        assertThat(isAnnotationPresent(Binds::class.java)).isTrue()
      }
    }
  }

  @Test fun `contributed bindings can replace other contributed bindings`() {
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesBinding
      $import

      interface ParentInterface

      @ContributesBinding(Any::class)
      interface ContributingInterface : ParentInterface
      
      @ContributesBinding(
          Any::class,
          replaces = [ContributingInterface::class]
      )
      interface SecondContributingInterface : ParentInterface
      
      $annotation(Any::class)
      interface ComponentInterface
      """
    ) {
      val modules = if (annotationClass == MergeModules::class) {
        componentInterface.daggerModule.includes.toList()
      } else {
        componentInterface.anyDaggerComponent.modules
      }
      assertThat(modules).containsExactly(componentInterface.anvilModule.kotlin)

      val methods = modules.first().java.declaredMethods
      assertThat(methods).hasLength(1)

      with(methods[0]) {
        assertThat(returnType).isEqualTo(parentInterface)
        assertThat(parameterTypes.toList()).containsExactly(secondContributingInterface)
        assertThat(isAbstract).isTrue()
        assertThat(isAnnotationPresent(Binds::class.java)).isTrue()
      }
    }
  }

  @Test fun `contributed bindings for an object can replace other contributed bindings`() {
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesBinding
      $import

      interface ParentInterface

      @ContributesBinding(Any::class)
      interface ContributingInterface : ParentInterface
      
      @ContributesBinding(
          Any::class,
          replaces = [ContributingInterface::class]
      )
      object SecondContributingInterface : ParentInterface
      
      $annotation(Any::class)
      interface ComponentInterface
      """
    ) {
      val modules = if (annotationClass == MergeModules::class) {
        componentInterface.daggerModule.includes.toList()
      } else {
        componentInterface.anyDaggerComponent.modules
      }
      assertThat(modules).containsExactly(componentInterface.anvilModule.kotlin)

      val methods = modules.first().java.declaredMethods
      assertThat(methods).hasLength(1)

      with(methods[0]) {
        assertThat(returnType).isEqualTo(parentInterface)
        assertThat(parameterTypes.toList()).isEmpty()
        assertThat(isAbstract).isFalse()
        assertThat(isAnnotationPresent(Provides::class.java)).isTrue()
      }
    }
  }

  @Test
  fun `a Dagger module is generated for a merged class and added to the component without a package`() {
    compile(
      """
      $import
      
      $annotation(Any::class)
      interface ComponentInterface
      """
    ) {
      val modules = if (annotationClass == MergeModules::class) {
        classLoader.loadClass("ComponentInterface").daggerModule.includes.toList()
      } else {
        classLoader.loadClass("ComponentInterface").anyDaggerComponent.modules
      }
      assertThat(modules).containsExactly(
        classLoader
          .loadClass("ComponentInterface")
          .anvilModule
          .kotlin
      )
    }
  }

  @Test fun `the contributed binding class must not have a generic type parameter`() {
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesBinding
      $import

      interface ParentInterface<T, S>
      
      class SomeOtherType
      
      @ContributesBinding(Any::class)
      interface ContributingInterface :
              ParentInterface<Map<String, List<Pair<String, Int>>>, SomeOtherType>
      
      $annotation(Any::class)
      interface ComponentInterface
      """
    ) {
      assertThat(exitCode).isEqualTo(COMPILATION_ERROR)

      assertThat(messages).contains("Source0.kt: (6, 11)")
      assertThat(messages).contains(
        "Class com.squareup.test.ContributingInterface binds com.squareup.test.ParentInterface, " +
          "but the bound type contains type parameter(s) <T, S>. Type parameters in bindings " +
          "are not supported. This binding needs to be contributed in a Dagger module manually."
      )
    }
  }

  @Test fun `the contributed binding class must not have a generic type parameter with super type chain`() {
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesBinding
      $import

      interface ParentInterface<out OutputT : Any>
      interface MiddleInterface<out OutputT : Any> : ParentInterface<OutputT>
      
      class SomeOtherType
      
      @ContributesBinding(Any::class, ParentInterface::class)
      interface ContributingInterface : MiddleInterface<SomeOtherType>
      
      $annotation(Any::class)
      interface ComponentInterface
      """
    ) {
      assertThat(exitCode).isEqualTo(COMPILATION_ERROR)

      assertThat(messages).contains("Source0.kt: (6, 11)")
      assertThat(messages).contains(
        "Class com.squareup.test.ContributingInterface binds com.squareup.test.ParentInterface, " +
          "but the bound type contains type parameter(s) <OutputT>. Type parameters in " +
          "bindings are not supported. This binding needs to be contributed in a Dagger module " +
          "manually."
      )
    }
  }

  @Test fun `the contributed binding is not replaced by excluded modules`() {
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesBinding
      import com.squareup.anvil.annotations.ContributesTo
      $import

      interface ParentInterface

      @ContributesBinding(Any::class)
      interface ContributingInterface : ParentInterface
      
      @ContributesTo(
          Any::class,
          replaces = [ContributingInterface::class]
      )
      @dagger.Module
      abstract class DaggerModule1
      
      $annotation(Any::class, exclude = [DaggerModule1::class])
      interface ComponentInterface
      """
    ) {
      val modules = if (annotationClass == MergeModules::class) {
        componentInterface.daggerModule.includes.toList()
      } else {
        componentInterface.anyDaggerComponent.modules
      }
      assertThat(modules).containsExactly(componentInterface.anvilModule.kotlin)

      val methods = modules.first().java.declaredMethods
      assertThat(methods).hasLength(1)

      with(methods[0]) {
        assertThat(returnType).isEqualTo(parentInterface)
        assertThat(parameterTypes.toList()).containsExactly(contributingInterface)
        assertThat(isAbstract).isTrue()
        assertThat(isAnnotationPresent(Binds::class.java)).isTrue()
      }
    }
  }

  @Test fun `bindings are excluded only in one component`() {
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesBinding
      import com.squareup.anvil.annotations.ContributesTo
      $import

      interface ParentInterface
      
      @ContributesBinding(Any::class)
      interface ContributingInterface : ParentInterface
            
      $annotation(Any::class)
      interface ComponentInterface
            
      $annotation(
        scope = Any::class,
        exclude = [ContributingInterface::class]
      )
      interface ComponentInterface2
      """
    ) {
      val anvilModule1 = if (annotationClass == MergeModules::class) {
        componentInterface.daggerModule.includes.toList()
      } else {
        componentInterface.anyDaggerComponent.modules
      }.single()

      val anvilModule2 = if (annotationClass == MergeModules::class) {
        classLoader.loadClass("com.squareup.test.ComponentInterface2")
          .daggerModule.includes.toList()
      } else {
        classLoader.loadClass("com.squareup.test.ComponentInterface2")
          .anyDaggerComponent.modules
      }.single()

      assertThat(anvilModule1.java.declaredMethods).hasLength(1)
      assertThat(anvilModule2.java.declaredMethods).isEmpty()
    }
  }

  @Test fun `the super type can be evaluated for a delegated class`() {
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesBinding
      $import
      
      inline fun <reified T> noop(): T = TODO()
      
      interface ParentInterface

      @ContributesBinding(Any::class)
      class ContributingInterface : ParentInterface by noop()
      
      $annotation(Any::class)
      interface ComponentInterface
      """
    ) {
      val modules = if (annotationClass == MergeModules::class) {
        componentInterface.daggerModule.includes.toList()
      } else {
        componentInterface.anyDaggerComponent.modules
      }
      assertThat(modules).containsExactly(componentInterface.anvilModule.kotlin)

      val methods = modules.single().java.declaredMethods
      assertThat(methods).hasLength(1)

      with(methods[0]) {
        assertThat(returnType).isEqualTo(parentInterface)
        assertThat(parameterTypes.toList()).containsExactly(contributingInterface)
        assertThat(isAbstract).isTrue()
        assertThat(isAnnotationPresent(Binds::class.java)).isTrue()
      }
    }
  }

  private val Class<*>.anyDaggerComponent: AnyDaggerComponent
    get() = anyDaggerComponent(annotationClass)
}
