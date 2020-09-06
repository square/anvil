package com.squareup.anvil.compiler

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.annotations.MergeComponent
import com.squareup.anvil.annotations.MergeSubcomponent
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.COMPILATION_ERROR
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.INTERNAL_ERROR
import dagger.Component
import dagger.Subcomponent
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
      return listOf(MergeComponent::class, MergeSubcomponent::class)
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
      assertThat(exitCode).isEqualTo(COMPILATION_ERROR)
      // Position to the class.
      assertThat(messages).contains("Source.kt: (7, 11)")
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
      assertThat(exitCode).isEqualTo(COMPILATION_ERROR)
      // Position to the class.
      assertThat(messages).contains("Source.kt: (7, 16)")
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

      assertThat(componentInterfaceAnvilModule.declaredMethods).isEmpty()
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
      assertThat(exitCode).isEqualTo(COMPILATION_ERROR)
      // Position to the class.
      assertThat(messages).contains("Source.kt: (17, 16)")
      assertThat(messages).contains(
          "com.squareup.test.DaggerModule1 with scope kotlin.Any wants to replace " +
              "com.squareup.test.ContributingInterface with scope kotlin.Unit. The replacement " +
              "must use the same scope."
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
      assertThat(componentInterfaceAnvilModule.declaredMethods).hasLength(1)
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
      assertThat(exitCode).isEqualTo(COMPILATION_ERROR)
      // Position to the class.
      assertThat(messages).contains("Source.kt: (17, 11)")
      assertThat(messages).contains(
          "com.squareup.test.ContributingInterface with scope kotlin.Any wants to replace " +
              "com.squareup.test.DaggerModule1 with scope kotlin.Unit. The replacement must use " +
              "the same scope."
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
      assertThat(exitCode).isEqualTo(COMPILATION_ERROR)
      // Position to the class.
      assertThat(messages).contains("Source.kt: (13, 16)")
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
      assertThat(exitCode).isEqualTo(COMPILATION_ERROR)
      // Position to the class.
      assertThat(messages).contains("Source.kt: (15, 16)")
      assertThat(messages).contains(
          "com.squareup.test.DaggerModule2 with scope kotlin.Any wants to replace " +
              "com.squareup.test.DaggerModule1 with scope kotlin.Unit. The replacement must " +
              "use the same scope."
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
      assertThat(exitCode).isEqualTo(COMPILATION_ERROR)
      // Position to the class.
      assertThat(messages).contains("Source.kt: (20, 11)")
      assertThat(messages).contains(
          "com.squareup.test.ComponentInterface with scope kotlin.Any wants to exclude " +
              "com.squareup.test.DaggerModule1 with scope kotlin.Unit. The exclusion must use " +
              "the same scope."
      )
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
      assertThat(componentInterfaceAnvilModule.declaredMethods).isEmpty()
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
      assertThat(exitCode).isEqualTo(COMPILATION_ERROR)
      // Position to the class.
      assertThat(messages).contains("Source.kt: (17, 11)")
      assertThat(messages).contains(
          "com.squareup.test.ComponentInterface with scope kotlin.Any wants to exclude " +
              "com.squareup.test.ContributingInterface with scope kotlin.Unit. The exclusion " +
              "must use the same scope."
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
        "internal", "private", "protected"
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
        assertThat(exitCode).isEqualTo(COMPILATION_ERROR)
        // Position to the class.
        assertThat(messages).contains("Source.kt: (8, ")
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
      assertThat(exitCode).isEqualTo(INTERNAL_ERROR)
      assertThat(messages).contains("File being compiled: (10,18)")
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
      assertThat(exitCode).isEqualTo(COMPILATION_ERROR)
      assertThat(messages).contains("Source.kt: (19, 11)")
    }
  }

  private val Class<*>.anyDaggerComponent: AnyDaggerComponent
    get() = anyDaggerComponent(annotationClass)
}
