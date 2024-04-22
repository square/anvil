package com.squareup.anvil.compiler.codegen

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.annotations.MergeComponent
import com.squareup.anvil.annotations.MergeSubcomponent
import com.squareup.anvil.annotations.compat.MergeModules
import com.squareup.anvil.compiler.compile
import com.squareup.anvil.compiler.componentInterface
import com.squareup.anvil.compiler.contributingInterface
import com.squareup.anvil.compiler.flatMapArray
import com.squareup.anvil.compiler.generatedMultiBindingModule
import com.squareup.anvil.compiler.internal.testing.AnyDaggerComponent
import com.squareup.anvil.compiler.internal.testing.anyDaggerComponent
import com.squareup.anvil.compiler.internal.testing.daggerModule
import com.squareup.anvil.compiler.internal.testing.isAbstract
import com.squareup.anvil.compiler.isFullTestRun
import com.squareup.anvil.compiler.mergedModules
import com.squareup.anvil.compiler.parentInterface
import com.squareup.anvil.compiler.parentInterface1
import com.squareup.anvil.compiler.parentInterface2
import com.squareup.anvil.compiler.secondContributingInterface
import com.squareup.anvil.compiler.subcomponentInterface
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode
import dagger.Binds
import dagger.Provides
import dagger.multibindings.IntoSet
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import kotlin.reflect.KClass

@RunWith(Parameterized::class)
class BindingModuleMultibindingSetTest(
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

  @Test fun `the Dagger multibinding method is generated for non-objects`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesMultibinding
      $import

      interface ParentInterface

      interface Middle : ParentInterface

      @ContributesMultibinding(Any::class, ParentInterface::class)
      interface ContributingInterface : Middle

      $annotation(Any::class)
      interface ComponentInterface
      """,
    ) {
      val modules = if (annotationClass == MergeModules::class) {
        componentInterface.daggerModule.includes.toList()
      } else {
        componentInterface.anyDaggerComponent.modules
      }

      val methods = modules.single().java.declaredMethods
      assertThat(methods).hasLength(1)

      with(methods[0]) {
        assertThat(returnType).isEqualTo(parentInterface)
        assertThat(parameterTypes.toList()).containsExactly(contributingInterface)
        assertThat(isAbstract).isTrue()
        assertThat(isAnnotationPresent(Binds::class.java)).isTrue()
        assertThat(isAnnotationPresent(IntoSet::class.java)).isTrue()
      }
    }
  }

  @Test fun `the Dagger provider method is generated for objects`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesMultibinding
      $import

      interface ParentInterface

      interface Middle : ParentInterface

      @ContributesMultibinding(Any::class, ParentInterface::class)
      object ContributingInterface : Middle

      $annotation(Any::class)
      interface ComponentInterface
      """,
    ) {
      val modules = componentInterface.mergedModules(annotationClass).toList()
      assertThat(modules).containsExactly(contributingInterface.generatedMultiBindingModule.kotlin)

      val methods = modules.single().java.declaredMethods
      assertThat(methods).hasLength(1)

      with(methods[0]) {
        assertThat(returnType).isEqualTo(parentInterface)
        assertThat(parameterTypes.toList()).isEmpty()
        assertThat(isAbstract).isFalse()
        assertThat(isAnnotationPresent(Provides::class.java)).isTrue()
        assertThat(isAnnotationPresent(IntoSet::class.java)).isTrue()

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

      import com.squareup.anvil.annotations.ContributesMultibinding
      $import

      interface ParentInterface

      @ContributesMultibinding(Any::class)
      interface ContributingInterface : ParentInterface

      $annotation(Any::class)
      interface ComponentInterface
      """,
    ) {
      val modules = if (annotationClass == MergeModules::class) {
        componentInterface.daggerModule.includes.toList()
      } else {
        componentInterface.anyDaggerComponent.modules
      }

      val methods = modules.single().java.declaredMethods
      assertThat(methods).hasLength(1)

      with(methods[0]) {
        assertThat(returnType).isEqualTo(parentInterface)
        assertThat(parameterTypes.toList()).containsExactly(contributingInterface)
        assertThat(isAbstract).isTrue()
        assertThat(isAnnotationPresent(Binds::class.java)).isTrue()
        assertThat(isAnnotationPresent(IntoSet::class.java)).isTrue()
      }
    }
  }

  @Test fun `contributed multibindings can replace other contributed multibindings`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesMultibinding
      $import

      interface ParentInterface

      @ContributesMultibinding(Any::class)
      interface ContributingInterface : ParentInterface

      @ContributesMultibinding(
          Any::class,
          replaces = [ContributingInterface::class]
      )
      interface SecondContributingInterface : ParentInterface

      $annotation(Any::class)
      interface ComponentInterface
      """,
    ) {
      val modules = componentInterface.mergedModules(annotationClass).toList()
      assertThat(
        modules,
      ).containsExactly(secondContributingInterface.generatedMultiBindingModule.kotlin)

      val methods = modules.first().java.declaredMethods
      assertThat(methods).hasLength(1)

      with(methods[0]) {
        assertThat(returnType).isEqualTo(parentInterface)
        assertThat(parameterTypes.toList()).containsExactly(secondContributingInterface)
        assertThat(isAbstract).isTrue()
        assertThat(isAnnotationPresent(Binds::class.java)).isTrue()
        assertThat(isAnnotationPresent(IntoSet::class.java)).isTrue()
      }
    }
  }

  @Test fun `contributed multibindings for an object can replace other contributed bindings`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesMultibinding
      $import

      interface ParentInterface

      @ContributesMultibinding(Any::class)
      interface ContributingInterface : ParentInterface

      @ContributesMultibinding(
          Any::class,
          replaces = [ContributingInterface::class]
      )
      object SecondContributingInterface : ParentInterface

      $annotation(Any::class)
      interface ComponentInterface
      """,
    ) {
      val modules = componentInterface.mergedModules(annotationClass).toList()
      assertThat(
        modules,
      ).containsExactly(secondContributingInterface.generatedMultiBindingModule.kotlin)

      val methods = modules.first().java.declaredMethods
      assertThat(methods).hasLength(1)

      with(methods[0]) {
        assertThat(returnType).isEqualTo(parentInterface)
        assertThat(parameterTypes.toList()).isEmpty()
        assertThat(isAbstract).isFalse()
        assertThat(isAnnotationPresent(Provides::class.java)).isTrue()
        assertThat(isAnnotationPresent(IntoSet::class.java)).isTrue()
      }
    }
  }

  @Test fun `the contributed multibinding class must not have a generic type parameter`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesMultibinding
      $import

      interface ParentInterface<T, S>

      class SomeOtherType

      @ContributesMultibinding(Any::class, ParentInterface::class)
      interface ContributingInterface :
              ParentInterface<Map<String, List<Pair<String, Int>>>, SomeOtherType>

      $annotation(Any::class)
      interface ComponentInterface
      """,
      expectExitCode = KotlinCompilation.ExitCode.COMPILATION_ERROR,
    ) {

      assertThat(messages).contains("Source0.kt:6:11")
      assertThat(messages).contains(
        "Class com.squareup.test.ContributingInterface binds com.squareup.test.ParentInterface, " +
          "but the bound type contains type parameter(s) <T, S>. Type parameters in bindings " +
          "are not supported. This binding needs to be contributed in a Dagger module manually.",
      )
    }
  }

  @Test fun `you can contribute bindings and multibindings at the same time`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesBinding
      import com.squareup.anvil.annotations.ContributesMultibinding
      $import

      interface ParentInterface

      @ContributesBinding(Any::class)
      @ContributesMultibinding(Any::class)
      interface ContributingInterface : ParentInterface

      $annotation(Any::class)
      interface ComponentInterface
      """,
    ) {
      val methods = componentInterface.mergedModules(annotationClass)
        .flatMap { it.java.declaredMethods.toList() }

      assertThat(methods).hasSize(2)
    }
  }

  @Test fun `the contributed multibinding is not replaced by excluded modules`() {
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesMultibinding
      import com.squareup.anvil.annotations.ContributesTo
      $import

      interface ParentInterface

      @ContributesMultibinding(Any::class)
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
      assertThat(modules).containsExactly(contributingInterface.generatedMultiBindingModule.kotlin)

      val methods = modules.first().java.declaredMethods
      assertThat(methods).hasLength(1)

      with(methods[0]) {
        assertThat(returnType).isEqualTo(parentInterface)
        assertThat(parameterTypes.toList()).containsExactly(contributingInterface)
        assertThat(isAbstract).isTrue()
        assertThat(isAnnotationPresent(Binds::class.java)).isTrue()
        assertThat(isAnnotationPresent(IntoSet::class.java)).isTrue()
      }
    }
  }

  @Test fun `the Dagger multibinding method is generated for multiple contributed bindings`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesMultibinding
      $import

      interface ParentInterface

      interface Middle : ParentInterface

      @ContributesMultibinding(Any::class, ParentInterface::class)
      @ContributesMultibinding(Unit::class, ParentInterface::class)
      interface ContributingInterface : Middle

      $annotation(Any::class)
      interface ComponentInterface

      $annotation(Unit::class)
      interface SubcomponentInterface
      """,
    ) {
      with(
        componentInterface.mergedModules(annotationClass).single().java.declaredMethods.single(),
      ) {
        assertThat(returnType).isEqualTo(parentInterface)
        assertThat(parameterTypes.toList()).containsExactly(contributingInterface)
        assertThat(isAbstract).isTrue()
        assertThat(isAnnotationPresent(Binds::class.java)).isTrue()
        assertThat(isAnnotationPresent(IntoSet::class.java)).isTrue()
      }

      with(
        subcomponentInterface.mergedModules(annotationClass).single().java.declaredMethods.single(),
      ) {
        assertThat(returnType).isEqualTo(parentInterface)
        assertThat(parameterTypes.toList()).containsExactly(contributingInterface)
        assertThat(isAbstract).isTrue()
        assertThat(isAnnotationPresent(Binds::class.java)).isTrue()
        assertThat(isAnnotationPresent(IntoSet::class.java)).isTrue()
      }
    }
  }

  @Test fun `multiple contributions can have different bound types`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesMultibinding
      $import
      
      interface ParentInterface1
      interface ParentInterface2

      @ContributesMultibinding(Any::class, boundType = ParentInterface1::class)
      @ContributesMultibinding(Unit::class, boundType = ParentInterface2::class)
      class ContributingInterface : ParentInterface1, ParentInterface2

      $annotation(Any::class)
      interface ComponentInterface

      $annotation(Unit::class)
      interface SubcomponentInterface
      """,
    ) {
      with(
        componentInterface.mergedModules(annotationClass).single().java.declaredMethods.single(),
      ) {
        assertThat(returnType).isEqualTo(parentInterface1)
        assertThat(parameterTypes.toList()).containsExactly(contributingInterface)
        assertThat(isAbstract).isTrue()
        assertThat(isAnnotationPresent(Binds::class.java)).isTrue()
        assertThat(isAnnotationPresent(IntoSet::class.java)).isTrue()
      }

      with(
        subcomponentInterface.mergedModules(annotationClass).single().java.declaredMethods.single(),
      ) {
        assertThat(returnType).isEqualTo(parentInterface2)
        assertThat(parameterTypes.toList()).containsExactly(contributingInterface)
        assertThat(isAbstract).isTrue()
        assertThat(isAnnotationPresent(Binds::class.java)).isTrue()
        assertThat(isAnnotationPresent(IntoSet::class.java)).isTrue()
      }
    }
  }

  @Test fun `multiple contributions can have the same scope but different bound types`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesMultibinding
      $import
      
      interface ParentInterface1
      interface ParentInterface2

      @ContributesMultibinding(Any::class, boundType = ParentInterface1::class)
      @ContributesMultibinding(Any::class, boundType = ParentInterface2::class)
      class ContributingInterface : ParentInterface1, ParentInterface2

      $annotation(Any::class)
      interface ComponentInterface
      """,
    ) {
      val methods = componentInterface.mergedModules(annotationClass)
        .flatMapArray { moduleClass -> moduleClass.java.declaredMethods }
      assertThat(methods).hasSize(2)

      // Sorting changes across unix and windows, so we just do a manual grab
      val method1 = methods.single { it.returnType == parentInterface1 }
      val method2 = methods.single { it.returnType == parentInterface2 }

      with(method1) {
        assertThat(returnType).isEqualTo(parentInterface1)
        assertThat(parameterTypes.toList()).containsExactly(contributingInterface)
        assertThat(isAbstract).isTrue()
        assertThat(isAnnotationPresent(Binds::class.java)).isTrue()
      }
      with(method2) {
        assertThat(returnType).isEqualTo(parentInterface2)
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

      import com.squareup.anvil.annotations.ContributesMultibinding
      import com.squareup.anvil.annotations.ContributesTo
      $import
      
      interface ParentInterface1
      interface ParentInterface2

      @ContributesMultibinding(Any::class, boundType = ParentInterface1::class)
      @ContributesMultibinding(Any::class, boundType = ParentInterface2::class)
      class ContributingInterface : ParentInterface1, ParentInterface2

      @ContributesTo(Any::class, replaces = [ContributingInterface::class])
      @dagger.Module
      abstract class DaggerModule1

      $annotation(Any::class)
      interface ComponentInterface
      """,
    ) {
      assertThat(
        componentInterface.mergedModules(annotationClass).flatMap {
          it.java.declaredMethods.toList()
        },
      ).isEmpty()
    }
  }

  private val Class<*>.anyDaggerComponent: AnyDaggerComponent
    get() = anyDaggerComponent(annotationClass)
}
