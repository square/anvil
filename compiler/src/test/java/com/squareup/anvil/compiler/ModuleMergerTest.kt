package com.squareup.anvil.compiler

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.annotations.MergeComponent
import com.squareup.anvil.annotations.MergeSubcomponent
import com.squareup.anvil.compiler.internal.testing.AnvilCompilation
import com.squareup.anvil.compiler.internal.testing.AnyDaggerComponent
import com.squareup.anvil.compiler.internal.testing.anyDaggerComponent
import com.squareup.anvil.compiler.internal.testing.daggerComponent
import com.squareup.anvil.compiler.internal.testing.daggerModule
import com.squareup.anvil.compiler.internal.testing.daggerSubcomponent
import com.squareup.anvil.compiler.internal.testing.withoutAnvilModule
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.OK
import dagger.Component
import dagger.Subcomponent
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import kotlin.reflect.KClass

@RunWith(Parameterized::class)
class ModuleMergerTest(
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
        }
      }
    }
  }

  @Test fun `Dagger modules are empty without arguments`() {
    compile(
      """
      package com.squareup.test
      
      $import
      
      $annotation(Any::class)
      interface ComponentInterface
      """
    ) {
      assertThat(componentInterface.anyDaggerComponent.modules.withoutAnvilModule()).isEmpty()

      if (annotationClass == MergeComponent::class) {
        assertThat(componentInterface.daggerComponent.dependencies).isEmpty()
      }
    }
  }

  @Test fun `modules are added in the Dagger component`() {
    compile(
      """
      package com.squareup.test
      
      $import
      
      $annotation(
          scope = Any::class,
          modules = [
            Boolean::class,
            Int::class
          ]
      )
      interface ComponentInterface
      """
    ) {
      val component = componentInterface.anyDaggerComponent
      assertThat(component.modules.withoutAnvilModule()).containsExactly(Boolean::class, Int::class)
    }
  }

  @Test fun `modules and dependencies are added in the Dagger component`() {
    assumeMergeComponent(annotationClass)

    compile(
      """
      package com.squareup.test
      
      $import
      
      $annotation(
          scope = Any::class,
          modules = [
            Boolean::class,
            Int::class
          ],
          dependencies = [
            Boolean::class,
            Int::class
          ]
      )
      interface ComponentInterface
      """
    ) {
      val component = componentInterface.daggerComponent
      assertThat(component.modules.withoutAnvilModule()).containsExactly(Boolean::class, Int::class)
      assertThat(component.dependencies.toList()).containsExactly(Boolean::class, Int::class)
    }
  }

  @Test
  fun `it's not allowed to have @Component and @MergeComponent annotation at the same time`() {
    val daggerComponentClass = when (annotationClass) {
      MergeComponent::class -> Component::class
      MergeSubcomponent::class -> Subcomponent::class
      else -> throw NotImplementedError()
    }

    compile(
      """
      package com.squareup.test
      
      $import
      
      $annotation(Any::class)
      @${daggerComponentClass.java.canonicalName}
      interface ComponentInterface
      """
    ) {
      assertThat(exitCode).isError()
      if (KOTLIN_PREVIEW) {
        assertThat(messages).contains("Source0.kt:7:11")
      } else {
        assertThat(messages).contains("Source0.kt: (7, 11)")
      }
    }
  }

  @Test fun `modules are merged`() {
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
      val component = componentInterface.anyDaggerComponent
      assertThat(component.modules.withoutAnvilModule()).containsExactly(daggerModule1.kotlin)
    }
  }

  @Test fun `module interfaces are merged`() {
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesTo
      $import
      
      @ContributesTo(Any::class)
      @dagger.Module
      interface DaggerModule1
      
      $annotation(Any::class)
      interface ComponentInterface
      """
    ) {
      val component = componentInterface.anyDaggerComponent
      assertThat(component.modules.withoutAnvilModule()).containsExactly(daggerModule1.kotlin)
    }
  }

  @Test fun `modules are merged with predefined modules`() {
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesTo
      $import
      
      @ContributesTo(Any::class)
      @dagger.Module
      abstract class DaggerModule1 
      
      $annotation(
          scope = Any::class,
          modules = [
            Boolean::class,
            Int::class
          ]
      )
      interface ComponentInterface
      """
    ) {
      val component = componentInterface.anyDaggerComponent
      assertThat(component.modules.withoutAnvilModule())
        .containsExactly(daggerModule1.kotlin, Int::class, Boolean::class)
    }
  }

  @Test fun `contributing module must be a Dagger Module`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesTo
      $import

      @ContributesTo(Any::class)
      abstract class DaggerModule1

      $annotation(Any::class)
      interface ComponentInterface
      """
    ) {
      assertThat(exitCode).isError()
      if (KOTLIN_PREVIEW) {
        assertThat(messages).contains("Source0.kt:7:16")
      } else {
        assertThat(messages).contains("Source0.kt: (7, 16)")
      }
    }
  }

  @Test fun `module can be replaced`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesTo
      $import

      @ContributesTo(Any::class)
      @dagger.Module
      abstract class DaggerModule1

      @ContributesTo(
          Any::class,
          replaces = [DaggerModule1::class]
      )
      @dagger.Module
      abstract class DaggerModule2 

      $annotation(Any::class)
      interface ComponentInterface
      """
    ) {
      val component = componentInterface.anyDaggerComponent
      assertThat(component.modules.withoutAnvilModule()).containsExactly(daggerModule2.kotlin)
    }
  }

  @Test fun `contributed binding can be replaced`() {
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

      $annotation(Any::class)
      interface ComponentInterface
      """
    ) {
      assertThat(componentInterface.anyDaggerComponent.modules.withoutAnvilModule())
        .containsExactly(daggerModule1.kotlin)

      assertThat(componentInterface.anvilModule.declaredMethods).isEmpty()
    }
  }

  @Test fun `contributed multibinding can be replaced`() {
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

      $annotation(Any::class)
      interface ComponentInterface
      """
    ) {
      assertThat(componentInterface.anyDaggerComponent.modules.withoutAnvilModule())
        .containsExactly(daggerModule1.kotlin)

      assertThat(componentInterface.anvilModule.declaredMethods).isEmpty()
    }
  }

  @Test fun `contributed binding can be replaced but must have the same scope`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesBinding
      import com.squareup.anvil.annotations.ContributesTo
      $import

      interface ParentInterface

      @ContributesBinding(Unit::class)
      interface ContributingInterface : ParentInterface

      @ContributesTo(
          Any::class,
          replaces = [ContributingInterface::class]
      )
      @dagger.Module
      abstract class DaggerModule1

      $annotation(Any::class)
      interface ComponentInterface
      """
    ) {
      assertThat(exitCode).isError()
      if (KOTLIN_PREVIEW) {
        assertThat(messages).contains("Source0.kt:17:16")
      } else {
        assertThat(messages).contains("Source0.kt: (17, 16)")
      }
      assertThat(messages).contains(
        "com.squareup.test.DaggerModule1 with scopes [kotlin.Any] wants to replace " +
          "com.squareup.test.ContributingInterface, but the replaced class isn't contributed " +
          "to the same scope."
      )
    }
  }

  @Test fun `contributed multibinding can be replaced but must have the same scope`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesMultibinding
      import com.squareup.anvil.annotations.ContributesTo
      $import

      interface ParentInterface

      @ContributesMultibinding(Unit::class)
      interface ContributingInterface : ParentInterface

      @ContributesTo(
          Any::class,
          replaces = [ContributingInterface::class]
      )
      @dagger.Module
      abstract class DaggerModule1

      $annotation(Any::class)
      interface ComponentInterface
      """
    ) {
      assertThat(exitCode).isError()
      if (KOTLIN_PREVIEW) {
        assertThat(messages).contains("Source0.kt:17:16")
      } else {
        assertThat(messages).contains("Source0.kt: (17, 16)")
      }
      assertThat(messages).contains(
        "com.squareup.test.DaggerModule1 with scopes [kotlin.Any] wants to replace " +
          "com.squareup.test.ContributingInterface, but the replaced class isn't contributed " +
          "to the same scope."
      )
    }
  }

  @Test fun `module can be replaced by contributed binding`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesBinding
      import com.squareup.anvil.annotations.ContributesTo
      $import

      interface ParentInterface

      @ContributesTo(Any::class)
      @dagger.Module
      abstract class DaggerModule1

      @ContributesBinding(
          Any::class,
          replaces = [DaggerModule1::class]
      )
      interface ContributingInterface : ParentInterface

      $annotation(Any::class)
      interface ComponentInterface
      """
    ) {
      assertThat(componentInterface.anyDaggerComponent.modules.withoutAnvilModule()).isEmpty()
      assertThat(componentInterface.anvilModule.declaredMethods).hasLength(1)
    }
  }

  @Test fun `module can be replaced by contributed multibinding`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesMultibinding
      import com.squareup.anvil.annotations.ContributesTo
      $import

      interface ParentInterface

      @ContributesTo(Any::class)
      @dagger.Module
      abstract class DaggerModule1

      @ContributesMultibinding(
          Any::class,
          replaces = [DaggerModule1::class]
      )
      interface ContributingInterface : ParentInterface

      $annotation(Any::class)
      interface ComponentInterface
      """
    ) {
      assertThat(componentInterface.anyDaggerComponent.modules.withoutAnvilModule()).isEmpty()
      assertThat(componentInterface.anvilModule.declaredMethods).hasLength(1)
    }
  }

  @Test fun `module replaced by contributed binding must use the same scope`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesBinding
      import com.squareup.anvil.annotations.ContributesTo
      $import

      interface ParentInterface

      @ContributesTo(Unit::class)
      @dagger.Module
      abstract class DaggerModule1

      @ContributesBinding(
          Any::class,
          replaces = [DaggerModule1::class]
      )
      interface ContributingInterface : ParentInterface

      $annotation(Any::class)
      interface ComponentInterface
      """
    ) {
      assertThat(exitCode).isError()
      if (KOTLIN_PREVIEW) {
        assertThat(messages).contains("Source0.kt:17:11")
      } else {
        assertThat(messages).contains("Source0.kt: (17, 11)")
      }
      assertThat(messages).contains(
        "com.squareup.test.ContributingInterface with scopes [kotlin.Any] wants to replace " +
          "com.squareup.test.DaggerModule1, but the replaced class isn't contributed " +
          "to the same scope."
      )
    }
  }

  @Test fun `module replaced by contributed multibinding must use the same scope`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesMultibinding
      import com.squareup.anvil.annotations.ContributesTo
      $import

      interface ParentInterface

      @ContributesTo(Unit::class)
      @dagger.Module
      abstract class DaggerModule1

      @ContributesMultibinding(
          Any::class,
          replaces = [DaggerModule1::class]
      )
      interface ContributingInterface : ParentInterface

      $annotation(Any::class)
      interface ComponentInterface
      """
    ) {
      assertThat(exitCode).isError()
      if (KOTLIN_PREVIEW) {
        assertThat(messages).contains("Source0.kt:17:11")
      } else {
        assertThat(messages).contains("Source0.kt: (17, 11)")
      }
      assertThat(messages).contains(
        "com.squareup.test.ContributingInterface with scopes [kotlin.Any] wants to replace " +
          "com.squareup.test.DaggerModule1, but the replaced class isn't contributed " +
          "to the same scope."
      )
    }
  }

  @Test fun `replaced modules must be Dagger modules`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesTo
      $import

      abstract class DaggerModule1

      @ContributesTo(
          Any::class,
          replaces = [DaggerModule1::class]
      )
      @dagger.Module
      abstract class DaggerModule2 

      $annotation(Any::class)
      interface ComponentInterface
      """
    ) {
      assertThat(exitCode).isError()
      if (KOTLIN_PREVIEW) {
        assertThat(messages).contains("Source0.kt:13:16")
      } else {
        assertThat(messages).contains("Source0.kt: (13, 16)")
      }
    }
  }

  @Test fun `replaced modules must use the same scope`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesTo
      $import

      @ContributesTo(Unit::class)
      @dagger.Module
      abstract class DaggerModule1

      @ContributesTo(
          Any::class,
          replaces = [DaggerModule1::class]
      )
      @dagger.Module
      abstract class DaggerModule2 

      $annotation(Any::class)
      interface ComponentInterface
      """
    ) {
      assertThat(exitCode).isError()
      if (KOTLIN_PREVIEW) {
        assertThat(messages).contains("Source0.kt:15:16")
      } else {
        assertThat(messages).contains("Source0.kt: (15, 16)")
      }
      assertThat(messages).contains(
        "com.squareup.test.DaggerModule2 with scopes [kotlin.Any] wants to replace " +
          "com.squareup.test.DaggerModule1, but the replaced class isn't contributed " +
          "to the same scope."
      )
    }
  }

  @Test fun `predefined modules are not replaced`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesTo
      $import

      @dagger.Module
      @ContributesTo(Any::class)
      abstract class DaggerModule1

      @ContributesTo(
          Any::class,
          replaces = [DaggerModule1::class]
      )
      @dagger.Module
      abstract class DaggerModule2 

      $annotation(
          scope = Any::class,
          modules = [
            DaggerModule1::class
          ]
      )
      interface ComponentInterface
      """
    ) {
      val component = componentInterface.anyDaggerComponent
      assertThat(component.modules.withoutAnvilModule())
        .containsExactly(daggerModule2.kotlin, daggerModule1.kotlin)
    }
  }

  @Test fun `modules can be excluded`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesTo
      $import

      @ContributesTo(Any::class)
      @dagger.Module
      abstract class DaggerModule1

      @ContributesTo(Any::class)
      @dagger.Module
      abstract class DaggerModule2 

      $annotation(
          scope = Any::class,
          exclude = [
            DaggerModule1::class
          ]
      )
      interface ComponentInterface
      """
    ) {
      val component = componentInterface.anyDaggerComponent
      assertThat(component.modules.withoutAnvilModule()).containsExactly(daggerModule2.kotlin)
    }
  }

  @Test fun `excluded modules must use the same scope`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesTo
      $import

      @ContributesTo(Unit::class)
      @dagger.Module
      abstract class DaggerModule1

      @ContributesTo(Any::class)
      @dagger.Module
      abstract class DaggerModule2 

      $annotation(
          scope = Any::class,
          exclude = [
            DaggerModule1::class
          ]
      )
      interface ComponentInterface
      """
    ) {
      assertThat(exitCode).isError()
      if (KOTLIN_PREVIEW) {
        assertThat(messages).contains("Source0.kt:20:11")
      } else {
        assertThat(messages).contains("Source0.kt: (20, 11)")
      }
      assertThat(messages).contains(
        "com.squareup.test.ComponentInterface with scopes [kotlin.Any] wants to exclude " +
          "com.squareup.test.DaggerModule1, but the excluded class isn't contributed " +
          "to the same scope."
      )
    }
  }

  @Test fun `contributed modules cannot be replaced by excluded modules`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesTo
      $import

      @ContributesTo(Any::class, replaces = [DaggerModule2::class])
      @dagger.Module
      abstract class DaggerModule1

      @ContributesTo(Any::class)
      @dagger.Module
      abstract class DaggerModule2 

      $annotation(
          scope = Any::class,
          exclude = [
            DaggerModule1::class
          ]
      )
      interface ComponentInterface
      """
    ) {
      val component = componentInterface.anyDaggerComponent
      assertThat(component.modules.withoutAnvilModule()).containsExactly(daggerModule2.kotlin)
    }
  }

  @Test fun `contributed modules are only excluded in one module`() {
    compile(
      """
      package com.squareup.test
      
      $import
      import com.squareup.anvil.annotations.ContributesTo
        
      @ContributesTo(Any::class, replaces = [DaggerModule2::class])
      @dagger.Module
      abstract class DaggerModule1
      
      @ContributesTo(Any::class)
      @dagger.Module
      abstract class DaggerModule2 
            
      $annotation(Any::class)
      interface ComponentInterface
            
      $annotation(
        scope = Any::class,
        exclude = [DaggerModule1::class]
      )
      interface ComponentInterface2  
      """
    ) {
      val component1 = componentInterface.anyDaggerComponent
      assertThat(component1.modules.withoutAnvilModule()).containsExactly(daggerModule1.kotlin)

      val component2 =
        classLoader.loadClass("com.squareup.test.ComponentInterface2").anyDaggerComponent
      assertThat(component2.modules.withoutAnvilModule()).containsExactly(daggerModule2.kotlin)
    }
  }

  @Test fun `contributed bindings can be excluded`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesBinding
      $import

      interface ParentInterface

      @ContributesBinding(Any::class)
      interface ContributingInterface : ParentInterface

      $annotation(
          scope = Any::class,
          exclude = [
            ContributingInterface::class
          ]
      )
      interface ComponentInterface
      """
    ) {
      assertThat(componentInterface.anvilModule.declaredMethods).isEmpty()
    }
  }

  @Test fun `contributed multibindings can be excluded`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesMultibinding
      $import

      interface ParentInterface

      @ContributesMultibinding(Any::class)
      interface ContributingInterface : ParentInterface

      $annotation(
          scope = Any::class,
          exclude = [
            ContributingInterface::class
          ]
      )
      interface ComponentInterface
      """
    ) {
      assertThat(componentInterface.anvilModule.declaredMethods).isEmpty()
    }
  }

  @Test fun `contributed bindings can be excluded but must use the same scope`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesBinding
      $import

      interface ParentInterface

      @ContributesBinding(Unit::class)
      interface ContributingInterface : ParentInterface

      $annotation(
          scope = Any::class,
          exclude = [
            ContributingInterface::class
          ]
      )
      interface ComponentInterface
      """
    ) {
      assertThat(exitCode).isError()
      if (KOTLIN_PREVIEW) {
        assertThat(messages).contains("Source0.kt:17:11")
      } else {
        assertThat(messages).contains("Source0.kt: (17, 11)")
      }
      assertThat(messages).contains(
        "com.squareup.test.ComponentInterface with scopes [kotlin.Any] wants to exclude " +
          "com.squareup.test.ContributingInterface, but the excluded class isn't contributed " +
          "to the same scope."
      )
    }
  }

  @Test fun `contributed multibindings can be excluded but must use the same scope`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesMultibinding
      $import

      interface ParentInterface

      @ContributesMultibinding(Unit::class)
      interface ContributingInterface : ParentInterface

      $annotation(
          scope = Any::class,
          exclude = [
            ContributingInterface::class
          ]
      )
      interface ComponentInterface
      """
    ) {
      assertThat(exitCode).isError()
      if (KOTLIN_PREVIEW) {
        assertThat(messages).contains("Source0.kt:17:11")
      } else {
        assertThat(messages).contains("Source0.kt: (17, 11)")
      }
      assertThat(messages).contains(
        "com.squareup.test.ComponentInterface with scopes [kotlin.Any] wants to exclude " +
          "com.squareup.test.ContributingInterface, but the excluded class isn't contributed " +
          "to the same scope."
      )
    }
  }

  @Test fun `modules are added to components with corresponding scope`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesTo
      $import

      @ContributesTo(Any::class)
      @dagger.Module
      abstract class DaggerModule1

      @ContributesTo(Unit::class)
      @dagger.Module
      abstract class DaggerModule2 

      $annotation(Any::class)
      interface ComponentInterface 

      $annotation(Unit::class)
      interface SubcomponentInterface
      """
    ) {
      assertThat(componentInterface.anyDaggerComponent.modules.withoutAnvilModule())
        .containsExactly(daggerModule1.kotlin)
      assertThat(subcomponentInterface.anyDaggerComponent.modules.withoutAnvilModule())
        .containsExactly(daggerModule2.kotlin)
    }
  }

  @Test fun `modules are added to components with corresponding scope and component type`() {
    assumeMergeComponent(annotationClass)

    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesTo
      import com.squareup.anvil.annotations.MergeComponent
      import com.squareup.anvil.annotations.MergeSubcomponent

      @ContributesTo(Any::class)
      @dagger.Module
      abstract class DaggerModule1

      @ContributesTo(Unit::class)
      @dagger.Module
      abstract class DaggerModule2 

      @MergeComponent(Any::class)
      interface ComponentInterface 

      @MergeSubcomponent(Unit::class)
      interface SubcomponentInterface
      """
    ) {
      assertThat(componentInterface.daggerComponent.modules.withoutAnvilModule())
        .containsExactly(daggerModule1.kotlin)
      assertThat(subcomponentInterface.daggerSubcomponent.modules.withoutAnvilModule())
        .containsExactly(daggerModule2.kotlin)
    }
  }

  @Test fun `contributed modules must be public`() {
    val visibilities = setOf(
      "internal",
      "private",
      "protected"
    )

    visibilities.forEach { visibility ->
      compile(
        """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesTo
        $import
        
        @ContributesTo(Any::class)
        @dagger.Module
        $visibility abstract class DaggerModule1
  
        $annotation(Any::class)
        interface ComponentInterface
        """
      ) {
        assertThat(exitCode).isError()
        if (KOTLIN_PREVIEW) {
          assertThat(messages).contains("Source0.kt:8:")
        } else {
          assertThat(messages).contains("Source0.kt: (8, ")
        }
      }
    }
  }

  @Test fun `inner modules are merged`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesTo
      $import
      
      interface ComponentInterface {
        @ContributesTo(Any::class)
        @dagger.Module
        abstract class InnerModule
      }
      
      $annotation(Any::class)
      interface SubcomponentInterface
      """
    ) {
      assertThat(subcomponentInterface.anyDaggerComponent.modules.withoutAnvilModule())
        .containsExactly(innerModule.kotlin)
    }
  }

  @Test fun `inner modules in a merged component fail`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesTo
      $import
      
      $annotation(Any::class)
      interface ComponentInterface {
        @ContributesTo(Any::class)
        @dagger.Module
        abstract class InnerModule
      }
      """
    ) {
      assertThat(exitCode).isError()
      assertThat(messages).contains(
        "Couldn't find the classId for <root>. Are we stuck in a loop while resolving super " +
          "types? Note that it's not supported to contribute an inner class to a scope that " +
          "is merged in an outer class."
      )
    }
  }

  @Test fun `inner modules in a merged component with different scope are merged`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesTo
      $import
      
      $annotation(Unit::class)
      interface ComponentInterface
      
      $annotation(Any::class)
      interface SubcomponentInterface {
        @ContributesTo(Unit::class)
        @dagger.Module
        abstract class InnerModule
      }
      """
    ) {
      val innerModule = classLoader
        .loadClass("com.squareup.test.SubcomponentInterface\$InnerModule")
      assertThat(componentInterface.anyDaggerComponent.modules.withoutAnvilModule())
        .containsExactly(innerModule.kotlin)
      assertThat(subcomponentInterface.anyDaggerComponent.modules.withoutAnvilModule()).isEmpty()
    }
  }

  @Test fun `modules are merged without a package`() {
    compile(
      """
      import com.squareup.anvil.annotations.ContributesTo
      $import
      
      @ContributesTo(Any::class)
      @dagger.Module
      abstract class DaggerModule1
      
      $annotation(Any::class)
      interface ComponentInterface
      """
    ) {
      val component = classLoader.loadClass("ComponentInterface").anyDaggerComponent
      assertThat(component.modules.withoutAnvilModule())
        .containsExactly(classLoader.loadClass("DaggerModule1").kotlin)
    }
  }

  @Test fun `a module is not allowed to be included and excluded`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesTo
      $import

      @ContributesTo(Any::class)
      @dagger.Module
      abstract class DaggerModule1

      $annotation(
          scope = Any::class,
          modules = [
            DaggerModule1::class
          ],
          exclude = [
            DaggerModule1::class
          ]
      )
      interface ComponentInterface
      """
    ) {
      assertThat(exitCode).isError()
      if (KOTLIN_PREVIEW) {
        assertThat(messages).contains("Source0.kt:19:11")
      } else {
        assertThat(messages).contains("Source0.kt: (19, 11)")
      }
    }
  }

  @Test fun `an error is thrown when the scope parameter is missing`() {
    compile(
      """
      package com.squareup.test
      
      $import
      
      $annotation
      interface ComponentInterface
      """
    ) {
      assertThat(exitCode).isError()
      assertThat(messages).contains(
        "Couldn't find scope for ${annotationClass.java.canonicalName}"
      )
    }
  }

  @Test fun `merged modules can be contributed to another scope at the same time`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.compat.MergeModules
      import com.squareup.anvil.annotations.ContributesTo
      import dagger.Module
      $import

      @ContributesTo(Any::class)
      @Module
      abstract class DaggerModule2

      @MergeModules(Any::class)
      @ContributesTo(Unit::class)
      class DaggerModule1
      
      $annotation(Unit::class)
      interface ComponentInterface
      """
    ) {
      assertThat(daggerModule1.daggerModule.includes.withoutAnvilModule())
        .containsExactly(daggerModule2.kotlin)

      val component = componentInterface.anyDaggerComponent
      assertThat(component.modules.withoutAnvilModule()).containsExactly(daggerModule1.kotlin)
    }
  }

  @Test fun `locally defined classes without a classId are skipped over when merging modules`() {
    assumeTrue(USE_IR)

    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesTo
      import dagger.Module
      import dagger.Provides
      $import

      class Utils {
        companion object {
          val EXTRA_UTILS: ExtraUtils
            get() {
              // This is the key piece that we're making sure gets skipped during merging
              class ExtraUtilsImpl : ExtraUtils {
                override fun doSomething() = Unit
              }
              return ExtraUtilsImpl() 
            }
        }
      }

      interface ExtraUtils {
        fun doSomething()
      }

      @ContributesTo(Any::class)
      @Module
      class DaggerModule1 {
        @Provides
        fun provideFunction(): Utils {
          return Utils()
        }
      }

      $annotation(scope = Any::class)
      interface ComponentInterface
      """
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }

  @Test fun `modules contributed to multiple scopes are merged`() {
    assumeIrBackend()

    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesTo
      $import
      
      @ContributesTo(Any::class)
      @ContributesTo(Unit::class)
      @dagger.Module
      abstract class DaggerModule1
      
      @ContributesTo(Any::class)
      @dagger.Module
      abstract class DaggerModule2
      
      $annotation(Any::class)
      interface ComponentInterface
      
      $annotation(Unit::class)
      interface SubcomponentInterface
      """
    ) {
      assertThat(componentInterface.anyDaggerComponent.modules.withoutAnvilModule())
        .containsExactly(daggerModule1.kotlin, daggerModule2.kotlin)

      assertThat(subcomponentInterface.anyDaggerComponent.modules.withoutAnvilModule())
        .containsExactly(daggerModule1.kotlin)
    }
  }

  @Test fun `modules contributed to multiple scopes are merged with multiple compilations`() {
    assumeIrBackend()

    val firstResult = compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesTo
      import dagger.Module
        
      @ContributesTo(Any::class)
      @ContributesTo(Unit::class)
      @Module
      abstract class DaggerModule1
      """
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }

    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesTo
      $import
      
      @ContributesTo(Any::class)
      @dagger.Module
      abstract class DaggerModule2
      
      $annotation(Any::class)
      interface ComponentInterface
      
      $annotation(Unit::class)
      interface SubcomponentInterface
      """,
      previousCompilationResult = firstResult
    ) {
      assertThat(componentInterface.anyDaggerComponent.modules.withoutAnvilModule())
        .containsExactly(daggerModule1.kotlin, daggerModule2.kotlin)

      assertThat(subcomponentInterface.anyDaggerComponent.modules.withoutAnvilModule())
        .containsExactly(daggerModule1.kotlin)
    }
  }

  @Test fun `modules contributed to multiple scopes can be replaced`() {
    assumeIrBackend()

    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesTo
      $import
      
      @ContributesTo(Any::class)
      @ContributesTo(Unit::class)
      @dagger.Module
      abstract class DaggerModule1
      
      @ContributesTo(Any::class, replaces = [DaggerModule1::class])
      @dagger.Module
      abstract class DaggerModule2
      
      $annotation(Any::class)
      interface ComponentInterface
      
      $annotation(Unit::class)
      interface SubcomponentInterface
      """
    ) {
      assertThat(componentInterface.anyDaggerComponent.modules.withoutAnvilModule())
        .containsExactly(daggerModule2.kotlin)

      assertThat(subcomponentInterface.anyDaggerComponent.modules.withoutAnvilModule())
        .containsExactly(daggerModule1.kotlin)
    }
  }

  @Test
  fun `replaced module contributed to multiple scopes must use the same scope`() {
    assumeIrBackend()

    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesTo
      $import
      
      @ContributesTo(Any::class)
      @ContributesTo(Unit::class)
      @dagger.Module
      abstract class DaggerModule1
      
      @ContributesTo(Int::class, replaces = [DaggerModule1::class])
      @dagger.Module
      abstract class DaggerModule2
      
      $annotation(Any::class)
      interface ComponentInterface
      
      $annotation(Unit::class)
      interface SubcomponentInterface1
      
      $annotation(Int::class)
      interface SubcomponentInterface2
      """
    ) {
      assertThat(exitCode).isError()
      assertThat(messages).contains(
        "com.squareup.test.DaggerModule2 with scopes [kotlin.Int] wants to replace " +
          "com.squareup.test.DaggerModule1, but the replaced class isn't contributed to the " +
          "same scope."
      )
    }
  }

  @Test fun `modules contributed to multiple scopes can be excluded in one scope`() {
    assumeIrBackend()

    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesTo
      $import
      
      @ContributesTo(Any::class)
      @ContributesTo(Unit::class)
      @dagger.Module
      abstract class DaggerModule1
      
      @ContributesTo(Any::class)
      @dagger.Module
      abstract class DaggerModule2
      
      $annotation(Any::class, exclude = [DaggerModule1::class])
      interface ComponentInterface
      
      $annotation(Unit::class)
      interface SubcomponentInterface
      """
    ) {
      assertThat(componentInterface.anyDaggerComponent.modules.withoutAnvilModule())
        .containsExactly(daggerModule2.kotlin)

      assertThat(subcomponentInterface.anyDaggerComponent.modules.withoutAnvilModule())
        .containsExactly(daggerModule1.kotlin)
    }
  }

  @Test fun `contributed modules in the old format are picked up`() {
    val result = AnvilCompilation()
      .useIR(USE_IR)
      .configureAnvil(enableAnvil = false)
      .compile(
        """
        package com.squareup.test
      
        import com.squareup.anvil.annotations.ContributesTo
        import dagger.Module
        
        @ContributesTo(Any::class)
        @Module
        abstract class DaggerModule1  
        """,
        """
        package anvil.hint.merge.com.squareup.test

        import com.squareup.test.DaggerModule1
        import kotlin.reflect.KClass
        
        public val com_squareup_test_DaggerModule1_reference: KClass<DaggerModule1> = DaggerModule1::class
        
        // Note that the number is missing after the scope. 
        public val com_squareup_test_DaggerModule1_scope: KClass<Any> = Any::class
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
      assertThat(componentInterface.anyDaggerComponent.modules.withoutAnvilModule())
        .containsExactly(daggerModule1.kotlin)
    }
  }

  private val Class<*>.anyDaggerComponent: AnyDaggerComponent
    get() = anyDaggerComponent(annotationClass)
}
