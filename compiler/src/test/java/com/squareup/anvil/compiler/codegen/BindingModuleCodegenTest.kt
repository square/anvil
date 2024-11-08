package com.squareup.anvil.compiler.codegen

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.annotations.MergeComponent
import com.squareup.anvil.annotations.MergeSubcomponent
import com.squareup.anvil.annotations.compat.MergeModules
import com.squareup.anvil.compiler.compile
import com.squareup.anvil.compiler.componentInterface
import com.squareup.anvil.compiler.contributingInterface
import com.squareup.anvil.compiler.daggerModule1
import com.squareup.anvil.compiler.flatMapArray
import com.squareup.anvil.compiler.generatedBindingModule
import com.squareup.anvil.compiler.internal.testing.AnyDaggerComponent
import com.squareup.anvil.compiler.internal.testing.anyDaggerComponent
import com.squareup.anvil.compiler.internal.testing.isAbstract
import com.squareup.anvil.compiler.isFullTestRun
import com.squareup.anvil.compiler.mergedModules
import com.squareup.anvil.compiler.parentInterface
import com.squareup.anvil.compiler.parentInterface1
import com.squareup.anvil.compiler.parentInterface2
import com.squareup.anvil.compiler.secondContributingInterface
import com.squareup.anvil.compiler.subcomponentInterface
import dagger.Binds
import dagger.Provides
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.ExitCode.OK
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import kotlin.reflect.KClass

@RunWith(Parameterized::class)
class BindingModuleCodegenTest(
  private val annotationClass: KClass<out Annotation>,
) {

  private val annotation = "@${annotationClass.simpleName}"
  private val import = "import ${annotationClass.java.canonicalName}"

  companion object {
    @Parameters(name = "{0}")
    @JvmStatic
    fun annotationClasses(): Collection<Any> {
      return buildList {
        add(MergeComponent::class)
        if (isFullTestRun()) {
          add(MergeSubcomponent::class)
          add(MergeModules::class)
        }
      }
    }
  }

  @Test fun `binding modules are merged with other modules`() {
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
      """,
    ) {
      val modules = componentInterface.mergedModules(annotationClass).toList()
      assertThat(modules)
        .containsExactly(daggerModule1.kotlin)
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
      """,
    ) {
      val modules = componentInterface.mergedModules(annotationClass).toList()
      assertThat(modules).containsExactly(contributingInterface.generatedBindingModule.kotlin)

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
      """,
    ) {
      val modules = componentInterface.mergedModules(annotationClass).toList()
      assertThat(modules).containsExactly(contributingInterface.generatedBindingModule.kotlin)

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
      """,
    ) {
      val modules = componentInterface.mergedModules(annotationClass).toList()
      assertThat(modules).containsExactly(contributingInterface.generatedBindingModule.kotlin)

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
      """,
    ) {
      val modules = componentInterface.mergedModules(annotationClass).toList()
      assertThat(modules).containsExactly(secondContributingInterface.generatedBindingModule.kotlin)

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
      """,
    ) {
      val modules = componentInterface.mergedModules(annotationClass).toList()
      assertThat(modules).containsExactly(secondContributingInterface.generatedBindingModule.kotlin)

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
      """,
      expectExitCode = ExitCode.COMPILATION_ERROR,
    ) {

      assertThat(messages).contains("Source0.kt:6:11")
      assertThat(messages).contains(
        "Class com.squareup.test.ContributingInterface binds com.squareup.test.ParentInterface, " +
          "but the bound type contains type parameter(s) <T, S>. Type parameters in bindings " +
          "are not supported. This binding needs to be contributed in a Dagger module manually.",
      )
    }
  }

  @Test
  fun `the contributed binding class must not have a generic type parameter with super type chain`() {
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
      """,
      expectExitCode = ExitCode.COMPILATION_ERROR,
    ) {

      assertThat(messages).contains("Source0.kt:6:11")
      assertThat(messages).contains(
        "Class com.squareup.test.ContributingInterface binds com.squareup.test.ParentInterface, " +
          "but the bound type contains type parameter(s) <OutputT>. Type parameters in " +
          "bindings are not supported. This binding needs to be contributed in a Dagger module " +
          "manually.",
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
      """,
    ) {
      val modules = componentInterface.mergedModules(annotationClass).toList()
      assertThat(modules).containsExactly(contributingInterface.generatedBindingModule.kotlin)

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
      """,
    ) {
      val componentInterfaceModules = componentInterface.mergedModules(
        annotationClass,
      ).toList().single()

      val componentInterface2Modules = classLoader.loadClass(
        "com.squareup.test.ComponentInterface2",
      ).mergedModules(annotationClass)

      assertThat(componentInterfaceModules.java.declaredMethods).hasLength(1)
      assertThat(componentInterface2Modules).isEmpty()
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
      """,
    ) {
      val modules = componentInterface.mergedModules(annotationClass).toList()
      assertThat(modules).containsExactly(contributingInterface.generatedBindingModule.kotlin)

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

  @Test fun `binding modules are generated for bindings contributed to multiple scopes`() {
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
      """,
    ) {
      listOf(componentInterface, subcomponentInterface).forEach { component ->
        with(
          component.mergedModules(
            annotationClass,
          ).flatMapArray { it.java.declaredMethods }.single(),
        ) {
          assertThat(returnType).isEqualTo(parentInterface)
          assertThat(parameterTypes.toList()).containsExactly(contributingInterface)
          assertThat(isAbstract).isTrue()
          assertThat(isAnnotationPresent(Binds::class.java)).isTrue()
        }
      }
    }
  }

  @Test
  fun `binding modules are generated for bindings contributed to multiple scopes with multiple compilations`() {
    val previousResult = compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesBinding

      interface ParentInterface

      @ContributesBinding(Any::class)
      @ContributesBinding(Unit::class)
      interface ContributingInterface : ParentInterface
      """,
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
      previousCompilationResult = previousResult,
    ) {
      listOf(componentInterface, subcomponentInterface).forEach { component ->
        with(
          component.mergedModules(
            annotationClass,
          ).flatMapArray { it.java.declaredMethods }.single(),
        ) {
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
      """,
    ) {
      with(
        componentInterface.mergedModules(annotationClass).flatMapArray {
          it.java.declaredMethods
        }.single(),
      ) {
        assertThat(returnType).isEqualTo(parentInterface1)
        assertThat(parameterTypes.toList()).containsExactly(contributingInterface)
        assertThat(isAbstract).isTrue()
        assertThat(isAnnotationPresent(Binds::class.java)).isTrue()
      }

      with(
        subcomponentInterface.mergedModules(annotationClass).flatMapArray {
          it.java.declaredMethods
        }.single(),
      ) {
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
      """,
    ) {
      val methods = componentInterface.mergedModules(annotationClass).flatMapArray {
        it.java.declaredMethods
      }
      assertThat(methods).hasSize(2)

      // Order of loading classes in the jvm here isn't super guaranteed across local and CI, so
      // we just look up the methods directly
      val parentInterface1Method = methods.find { it.returnType == parentInterface1 }
        ?: error("No method found for $parentInterface1")
      val parentInterface2Method = methods.find { it.returnType == parentInterface2 }
        ?: error("No method found for $parentInterface2")

      with(parentInterface1Method) {
        assertThat(returnType).isEqualTo(parentInterface1)
        assertThat(parameterTypes.toList()).containsExactly(contributingInterface)
        assertThat(isAbstract).isTrue()
        assertThat(isAnnotationPresent(Binds::class.java)).isTrue()
      }
      with(parentInterface2Method) {
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
      """,
    ) {
      val methods = componentInterface.mergedModules(annotationClass).flatMapArray { moduleClass ->
        moduleClass.java.declaredMethods
      }
      assertThat(methods).hasSize(2)
      val otherParentInterface = classLoader.loadClass("com.squareup.test.other.ParentInterface")

      // Order of loading classes in the jvm here isn't super guaranteed across local and CI, so
      // we just look up the methods directly
      val parentInterfaceMethod = methods.find { it.returnType == parentInterface }
        ?: error("No method found for $parentInterface")
      val otherParentInterfaceMethod = methods.find { it.returnType == otherParentInterface }
        ?: error("No method found for $otherParentInterface")

      with(otherParentInterfaceMethod) {
        assertThat(returnType).isEqualTo(
          classLoader.loadClass("com.squareup.test.other.ParentInterface"),
        )
        assertThat(parameterTypes.toList()).containsExactly(contributingInterface)
        assertThat(isAbstract).isTrue()
        assertThat(isAnnotationPresent(Binds::class.java)).isTrue()
      }
      with(parentInterfaceMethod) {
        assertThat(returnType).isEqualTo(parentInterface)
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
      """,
    ) {
      assertThat(
        componentInterface.mergedModules(annotationClass).flatMapArray {
          it.java.declaredMethods
        },
      ).isEmpty()
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
      """,
    ) {
      with(
        componentInterface.mergedModules(annotationClass).flatMapArray {
          it.java.declaredMethods
        }.single(),
      ) {
        assertThat(returnType).isEqualTo(parentInterface)
        assertThat(parameterTypes.toList()).containsExactly(secondContributingInterface)
        assertThat(isAbstract).isTrue()
        assertThat(isAnnotationPresent(Binds::class.java)).isTrue()
      }

      with(
        subcomponentInterface.mergedModules(annotationClass).flatMapArray {
          it.java.declaredMethods
        }.single(),
      ) {
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
      """,
    ) {
      with(
        componentInterface.mergedModules(annotationClass).flatMapArray {
          it.java.declaredMethods
        }.single(),
      ) {
        assertThat(returnType).isEqualTo(parentInterface)
        assertThat(parameterTypes.toList()).containsExactly(contributingInterface)
        assertThat(isAbstract).isTrue()
        assertThat(isAnnotationPresent(Binds::class.java)).isTrue()
      }

      assertThat(
        subcomponentInterface.mergedModules(annotationClass).flatMapArray {
          it.java.declaredMethods
        },
      ).isEmpty()
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
      """,
    ) {
      listOf(componentInterface, subcomponentInterface).forEach { component ->
        with(
          component.mergedModules(
            annotationClass,
          ).flatMapArray { it.java.declaredMethods }.single(),
        ) {
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
      """,
    ) {
      componentInterface.mergedModules(annotationClass).flatMapArray {
        it.java.declaredMethods
      }.forEach { method ->
        with(method) {
          assertThat(returnType).isEqualTo(parentInterface)
          assertThat(parameterTypes.toList()).containsExactly(contributingInterface)
          assertThat(isAbstract).isTrue()
          assertThat(isAnnotationPresent(Binds::class.java)).isTrue()
        }
      }
      assertThat(subcomponentInterface.mergedModules(annotationClass)).isEmpty()
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
      """,
    ) {
      val modules = componentInterface.mergedModules(annotationClass).toList()
      assertThat(modules)
        .containsExactly(daggerModule1.kotlin)
    }
  }

  private val Class<*>.anyDaggerComponent: AnyDaggerComponent
    get() = anyDaggerComponent(annotationClass)
}
