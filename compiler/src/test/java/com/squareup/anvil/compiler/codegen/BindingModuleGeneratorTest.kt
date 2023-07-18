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
import com.squareup.anvil.compiler.internal.testing.AnvilCompilation
import com.squareup.anvil.compiler.internal.testing.AnyDaggerComponent
import com.squareup.anvil.compiler.internal.testing.anyDaggerComponent
import com.squareup.anvil.compiler.internal.testing.daggerModule
import com.squareup.anvil.compiler.internal.testing.isAbstract
import com.squareup.anvil.compiler.isFullTestRun
import com.squareup.anvil.compiler.parentInterface
import com.squareup.anvil.compiler.parentInterface1
import com.squareup.anvil.compiler.parentInterface2
import com.squareup.anvil.compiler.secondContributingInterface
import com.squareup.anvil.compiler.subcomponentInterface
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.OK
import dagger.Binds
import dagger.Provides
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import java.io.File
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
      return buildList {
        add(MergeComponent::class)
        if (isFullTestRun()) {
          add(MergeSubcomponent::class)
          add(MergeModules::class)
        }
      }
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

  @Test fun `the Dagger binding method with star type is generated for generic type parameters`() {
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
      assertThat(exitCode).isEqualTo(ExitCode.OK)

      // Because of type erasure, we cannot check through the type system that generated type
      // is the right one. Instead, we can check generated string.

      val generatedModuleFile = File(outputDirectory.parent, "build/anvil")
        .walk()
        .single { it.isFile && it.name == "ComponentInterface.kt" }

      assertThat(generatedModuleFile.readText().replace(Regex("\\s"), ""))
        .contains(
          "bindParentInterface(contributingInterface: ContributingInterface):" +
            "ParentInterface<*, *>"
        )
    }
  }

  @Test
  fun `the Dagger binding method with star type is generated for a super type chain`() {
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
      assertThat(exitCode).isEqualTo(ExitCode.OK)

      // Because of type erasure, we cannot check through the type system that generated type
      // is the right one. Instead, we can check generated string.

      val generatedModuleFile = File(outputDirectory.parent, "build/anvil")
        .walk()
        .single { it.isFile && it.name == "ComponentInterface.kt" }

      assertThat(generatedModuleFile.readText().replace(Regex("\\s"), ""))
        .contains(
          "bindParentInterface(contributingInterface:ContributingInterface):" +
            "ParentInterface<*>"
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

  @Test fun `methods are generated for bindings contributed to multiple scopes`() {
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesBinding
      $import

      interface ParentInterface

      @ContributesBinding(Any::class)
      @ContributesBinding(Unit::class)
      interface ContributingInterface : ParentInterface
      
      $annotation(Any::class)
      interface ComponentInterface
      
      $annotation(Unit::class)
      interface SubcomponentInterface
      """
    ) {
      listOf(componentInterface, subcomponentInterface).forEach { component ->
        with(component.anvilModule.declaredMethods.single()) {
          assertThat(returnType).isEqualTo(parentInterface)
          assertThat(parameterTypes.toList()).containsExactly(contributingInterface)
          assertThat(isAbstract).isTrue()
          assertThat(isAnnotationPresent(Binds::class.java)).isTrue()
        }
      }
    }
  }

  @Test
  fun `methods are generated for bindings contributed to multiple scopes with multiple compilations`() {
    val previousResult = compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesBinding

      interface ParentInterface

      @ContributesBinding(Any::class)
      @ContributesBinding(Unit::class)
      interface ContributingInterface : ParentInterface
      """
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }

    compile(
      """
      package com.squareup.test
      
      $import

      $annotation(Any::class)
      interface ComponentInterface
      
      $annotation(Unit::class)
      interface SubcomponentInterface
      """,
      previousCompilationResult = previousResult
    ) {
      listOf(componentInterface, subcomponentInterface).forEach { component ->
        with(component.anvilModule.declaredMethods.single()) {
          assertThat(returnType).isEqualTo(parentInterface)
          assertThat(parameterTypes.toList()).containsExactly(contributingInterface)
          assertThat(isAbstract).isTrue()
          assertThat(isAnnotationPresent(Binds::class.java)).isTrue()
        }
      }
    }
  }

  @Test fun `multiple contributions can have different bound types`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesBinding
      $import
      
      interface ParentInterface1
      interface ParentInterface2

      @ContributesBinding(Any::class, boundType = ParentInterface1::class)
      @ContributesBinding(Unit::class, boundType = ParentInterface2::class)
      class ContributingInterface : ParentInterface1, ParentInterface2

      $annotation(Any::class)
      interface ComponentInterface
      
      $annotation(Unit::class)
      interface SubcomponentInterface
      """
    ) {
      with(componentInterface.anvilModule.declaredMethods.single()) {
        assertThat(returnType).isEqualTo(parentInterface1)
        assertThat(parameterTypes.toList()).containsExactly(contributingInterface)
        assertThat(isAbstract).isTrue()
        assertThat(isAnnotationPresent(Binds::class.java)).isTrue()
      }

      with(subcomponentInterface.anvilModule.declaredMethods.single()) {
        assertThat(returnType).isEqualTo(parentInterface2)
        assertThat(parameterTypes.toList()).containsExactly(contributingInterface)
        assertThat(isAbstract).isTrue()
        assertThat(isAnnotationPresent(Binds::class.java)).isTrue()
      }
    }
  }

  @Test fun `multiple contributions to the same scope must have different bound types`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesBinding
      $import
      
      interface ParentInterface1
      interface ParentInterface2

      @ContributesBinding(Any::class, boundType = ParentInterface1::class)
      @ContributesBinding(Any::class, boundType = ParentInterface2::class)
      class ContributingInterface : ParentInterface1, ParentInterface2

      $annotation(Any::class)
      interface ComponentInterface
      """
    ) {
      val methods = componentInterface.anvilModule.declaredMethods.sortedBy { it.name }
      assertThat(methods).hasSize(2)

      with(methods[0]) {
        assertThat(returnType).isEqualTo(parentInterface1)
        assertThat(parameterTypes.toList()).containsExactly(contributingInterface)
        assertThat(isAbstract).isTrue()
        assertThat(isAnnotationPresent(Binds::class.java)).isTrue()
      }
      with(methods[1]) {
        assertThat(returnType).isEqualTo(parentInterface2)
        assertThat(parameterTypes.toList()).containsExactly(contributingInterface)
        assertThat(isAbstract).isTrue()
        assertThat(isAnnotationPresent(Binds::class.java)).isTrue()
      }
    }
  }

  @Test fun `multiple contributions using different bound types with the same simple name don't clash`() {
    compile(
      """
      package com.squareup.test.other

      interface ParentInterface
      """,
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesBinding
      $import
      
      interface ParentInterface : com.squareup.test.other.ParentInterface

      @ContributesBinding(Any::class, boundType = ParentInterface::class)
      @ContributesBinding(Any::class, boundType = com.squareup.test.other.ParentInterface::class)
      class ContributingInterface : ParentInterface

      $annotation(Any::class)
      interface ComponentInterface
      """
    ) {
      val methods = componentInterface.anvilModule.declaredMethods.sortedBy { it.name }
      assertThat(methods).hasSize(2)

      with(methods[0]) {
        assertThat(returnType).isEqualTo(parentInterface)
        assertThat(parameterTypes.toList()).containsExactly(contributingInterface)
        assertThat(isAbstract).isTrue()
        assertThat(isAnnotationPresent(Binds::class.java)).isTrue()
      }
      with(methods[1]) {
        assertThat(returnType).isEqualTo(
          classLoader.loadClass("com.squareup.test.other.ParentInterface")
        )
        assertThat(parameterTypes.toList()).containsExactly(contributingInterface)
        assertThat(isAbstract).isTrue()
        assertThat(isAnnotationPresent(Binds::class.java)).isTrue()
      }
    }
  }

  @Test fun `multiple contributions to the same scope can be replaced at once`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesBinding
      import com.squareup.anvil.annotations.ContributesTo
      $import
      
      interface ParentInterface1
      interface ParentInterface2

      @ContributesBinding(Any::class, boundType = ParentInterface1::class)
      @ContributesBinding(Any::class, boundType = ParentInterface2::class)
      class ContributingInterface : ParentInterface1, ParentInterface2

      @ContributesTo(Any::class, replaces = [ContributingInterface::class])
      @dagger.Module
      abstract class DaggerModule1

      $annotation(Any::class)
      interface ComponentInterface
      """
    ) {
      assertThat(componentInterface.anvilModule.declaredMethods).isEmpty()
    }
  }

  @Test
  fun `bindings contributed to multiple scopes can be replaced by other contributed bindings`() {
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesBinding
      $import

      interface ParentInterface

      @ContributesBinding(Any::class)
      @ContributesBinding(Unit::class)
      interface ContributingInterface : ParentInterface
      
      @ContributesBinding(
          Any::class,
          replaces = [ContributingInterface::class]
      )
      interface SecondContributingInterface : ParentInterface
      
      $annotation(Any::class)
      interface ComponentInterface
      
      $annotation(Unit::class)
      interface SubcomponentInterface
      """
    ) {
      with(componentInterface.anvilModule.declaredMethods.single()) {
        assertThat(returnType).isEqualTo(parentInterface)
        assertThat(parameterTypes.toList()).containsExactly(secondContributingInterface)
        assertThat(isAbstract).isTrue()
        assertThat(isAnnotationPresent(Binds::class.java)).isTrue()
      }

      with(subcomponentInterface.anvilModule.declaredMethods.single()) {
        assertThat(returnType).isEqualTo(parentInterface)
        assertThat(parameterTypes.toList()).containsExactly(contributingInterface)
        assertThat(isAbstract).isTrue()
        assertThat(isAnnotationPresent(Binds::class.java)).isTrue()
      }
    }
  }

  @Test fun `bindings contributed to multiple scopes can be excluded in one scope`() {
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesBinding
      import com.squareup.anvil.annotations.ContributesTo
      $import

      interface ParentInterface
      
      @ContributesBinding(Any::class)
      @ContributesBinding(Unit::class)
      interface ContributingInterface : ParentInterface
            
      $annotation(Any::class)
      interface ComponentInterface
            
      $annotation(
        scope = Unit::class,
        exclude = [ContributingInterface::class]
      )
      interface SubcomponentInterface
      """
    ) {
      with(componentInterface.anvilModule.declaredMethods.single()) {
        assertThat(returnType).isEqualTo(parentInterface)
        assertThat(parameterTypes.toList()).containsExactly(contributingInterface)
        assertThat(isAbstract).isTrue()
        assertThat(isAnnotationPresent(Binds::class.java)).isTrue()
      }

      assertThat(subcomponentInterface.anvilModule.declaredMethods).isEmpty()
    }
  }

  @Test fun `contributed bindings in the old format are picked up`() {
    val result = AnvilCompilation()
      .configureAnvil(enableAnvil = false)
      .compile(
        """
        package com.squareup.test
      
        import com.squareup.anvil.annotations.ContributesBinding
        
        interface ParentInterface
      
        @ContributesBinding(Any::class)
        interface ContributingInterface : ParentInterface  
        """,
        """
        package anvil.hint.binding.com.squareup.test

        import com.squareup.test.ContributingInterface
        import kotlin.reflect.KClass
        
        public val com_squareup_test_ContributingInterface_reference: KClass<ContributingInterface> = ContributingInterface::class
        
        // Note that the number is missing after the scope. 
        public val com_squareup_test_ContributingInterface_scope: KClass<Any> = Any::class
        """.trimIndent()
      ) {
        assertThat(exitCode).isEqualTo(OK)
      }

    compile(
      """
      package com.squareup.test
      
      $import
      
      $annotation(Any::class)
      interface ComponentInterface
      """,
      previousCompilationResult = result
    ) {
      with(componentInterface.anvilModule.declaredMethods.single()) {
        assertThat(returnType).isEqualTo(parentInterface)
        assertThat(parameterTypes.toList()).containsExactly(contributingInterface)
        assertThat(isAbstract).isTrue()
        assertThat(isAnnotationPresent(Binds::class.java)).isTrue()
      }
    }
  }

  @Test fun `a contributed binding is merged in multiple scopes`() {
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesBinding
      $import

      interface ParentInterface

      @ContributesBinding(Unit::class)
      interface ContributingInterface : ParentInterface
      
      $annotation(Any::class)
      $annotation(Unit::class)
      interface ComponentInterface
      
      $annotation(Int::class)
      $annotation(Unit::class)
      interface SubcomponentInterface
      """
    ) {
      listOf(componentInterface, subcomponentInterface).forEach { component ->
        with(component.anvilModule.declaredMethods.single()) {
          assertThat(returnType).isEqualTo(parentInterface)
          assertThat(parameterTypes.toList()).containsExactly(contributingInterface)
          assertThat(isAbstract).isTrue()
          assertThat(isAnnotationPresent(Binds::class.java)).isTrue()
        }
      }
    }
  }

  @Test fun `a contributed binding can be excluded in one component`() {
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesBinding
      $import

      interface ParentInterface

      @ContributesBinding(Unit::class)
      @ContributesBinding(Any::class)
      interface ContributingInterface : ParentInterface
      
      $annotation(Any::class)
      $annotation(Unit::class)
      interface ComponentInterface
      
      $annotation(Any::class)
      $annotation(Unit::class, exclude = [ContributingInterface::class])
      interface SubcomponentInterface
      """
    ) {
      with(componentInterface.anvilModule.declaredMethods.single()) {
        assertThat(returnType).isEqualTo(parentInterface)
        assertThat(parameterTypes.toList()).containsExactly(contributingInterface)
        assertThat(isAbstract).isTrue()
        assertThat(isAnnotationPresent(Binds::class.java)).isTrue()
      }
      assertThat(subcomponentInterface.anvilModule.declaredMethods).isEmpty()
    }
  }

  @Test fun `an unrelated annotation wrapped in backticks does not break type resolution`() {
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesTo
      $import
      
      @DslMarker
      annotation class `Fancy${'$'}DslMarker`

      @ContributesTo(Any::class)
      @dagger.Module
      abstract class DaggerModule1
      
      @`Fancy${'$'}DslMarker`
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

  private val Class<*>.anyDaggerComponent: AnyDaggerComponent
    get() = anyDaggerComponent(annotationClass)
}
