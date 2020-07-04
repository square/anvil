package com.squareup.hephaestus.compiler

import com.google.common.truth.Truth.assertThat
import com.squareup.hephaestus.annotations.MergeComponent
import com.squareup.hephaestus.annotations.MergeSubcomponent
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.COMPILATION_ERROR
import com.tschuchort.compiletesting.KotlinCompilation.Result
import dagger.Component
import dagger.Subcomponent
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import kotlin.reflect.KClass

@RunWith(Parameterized::class)
class ModuleMergerTest(
  private val annotationClass: KClass<*>,
  private val skipAnalysis: Boolean
) {

  private val annotation = "@${annotationClass.simpleName}"
  private val import = "import ${annotationClass.java.canonicalName}"

  companion object {
    @Parameters(name = "{0}, skipAnalysis: {1}")
    @JvmStatic fun annotationClasses(): Collection<Array<Any>> {
      return listOf(MergeComponent::class, MergeSubcomponent::class)
          .flatMap { clazz ->
            listOf(true, false).map { arrayOf(clazz, it) }
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
      assertThat(componentInterface.anyDaggerComponent.modules).isEmpty()

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
      assertThat(component.modules).containsExactly(Boolean::class, Int::class)
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
      assertThat(component.modules.toList()).containsExactly(Boolean::class, Int::class)
      assertThat(component.dependencies.toList()).containsExactly(Boolean::class, Int::class)
    }
  }

  @Test fun `it's not allowed to have @Component and @MergeComponent annotation at the same time`() {
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
        
        import com.squareup.hephaestus.annotations.ContributesTo
        $import
        
        @ContributesTo(Any::class)
        @dagger.Module
        abstract class DaggerModule1
        
        $annotation(Any::class)
        interface ComponentInterface
    """
    ) {
      val component = componentInterface.anyDaggerComponent
      assertThat(component.modules).containsExactly(daggerModule1.kotlin)
    }
  }

  @Test fun `module interfaces are merged`() {
    compile(
        """
        package com.squareup.test
        
        import com.squareup.hephaestus.annotations.ContributesTo
        $import
        
        @ContributesTo(Any::class)
        @dagger.Module
        interface DaggerModule1
        
        $annotation(Any::class)
        interface ComponentInterface
    """
    ) {
      val component = componentInterface.anyDaggerComponent
      assertThat(component.modules).containsExactly(daggerModule1.kotlin)
    }
  }

  @Test fun `modules are merged with predefined modules`() {
    compile(
        """
        package com.squareup.test
        
        import com.squareup.hephaestus.annotations.ContributesTo
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
      assertThat(component.modules)
          .containsExactly(daggerModule1.kotlin, Int::class, Boolean::class)
    }
  }

  @Test fun `contributing module must be a Dagger Module`() {
    compile(
        """
        package com.squareup.test

        import com.squareup.hephaestus.annotations.ContributesTo
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

        import com.squareup.hephaestus.annotations.ContributesTo
        $import

        @ContributesTo(Any::class)
        @dagger.Module
        abstract class DaggerModule1

        @ContributesTo(
            Any::class,
            replaces = DaggerModule1::class
        )
        @dagger.Module
        abstract class DaggerModule2 

        $annotation(Any::class)
        interface ComponentInterface
    """
    ) {
      val component = componentInterface.anyDaggerComponent
      assertThat(component.modules).containsExactly(daggerModule2.kotlin)
    }
  }

  @Test fun `replaced modules must be Dagger modules`() {
    compile(
        """
        package com.squareup.test

        import com.squareup.hephaestus.annotations.ContributesTo
        $import

        abstract class DaggerModule1

        @ContributesTo(
            Any::class,
            replaces = DaggerModule1::class
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

  @Test fun `predefined modules are not replaced`() {
    compile(
        """
        package com.squareup.test

        import com.squareup.hephaestus.annotations.ContributesTo
        $import

        @dagger.Module
        abstract class DaggerModule1

        @ContributesTo(
            Any::class,
            replaces = DaggerModule1::class
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
      assertThat(component.modules)
          .containsExactly(daggerModule2.kotlin, daggerModule1.kotlin)
    }
  }

  @Test fun `modules can be excluded`() {
    compile(
        """
        package com.squareup.test

        import com.squareup.hephaestus.annotations.ContributesTo
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
      assertThat(component.modules).containsExactly(daggerModule2.kotlin)
    }
  }

  @Test fun `predefined modules are not excluded`() {
    compile(
        """
        package com.squareup.test

        import com.squareup.hephaestus.annotations.ContributesTo
        $import

        @ContributesTo(Any::class)
        @dagger.Module
        abstract class DaggerModule1

        @ContributesTo(Any::class)
        @dagger.Module
        abstract class DaggerModule2 

        $annotation(
            scope = Any::class,
            modules = [
              DaggerModule1::class
            ],
            exclude = [
              DaggerModule1::class,
              DaggerModule2::class
            ]
        )
        interface ComponentInterface
    """
    ) {
      val component = componentInterface.anyDaggerComponent
      assertThat(component.modules).containsExactly(daggerModule1.kotlin)
    }
  }

  @Test fun `modules are added to components with corresponding scope`() {
    compile(
        """
        package com.squareup.test

        import com.squareup.hephaestus.annotations.ContributesTo
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
      assertThat(componentInterface.anyDaggerComponent.modules)
          .containsExactly(daggerModule1.kotlin)
      assertThat(subcomponentInterface.anyDaggerComponent.modules)
          .containsExactly(daggerModule2.kotlin)
    }
  }

  @Test fun `modules are added to components with corresponding scope and component type`() {
    assumeMergeComponent(annotationClass)

    compile(
        """
        package com.squareup.test

        import com.squareup.hephaestus.annotations.ContributesTo
        import com.squareup.hephaestus.annotations.MergeComponent
        import com.squareup.hephaestus.annotations.MergeSubcomponent

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
      assertThat(componentInterface.daggerComponent.modules.toList())
          .containsExactly(daggerModule1.kotlin)
      assertThat(subcomponentInterface.daggerSubcomponent.modules.toList())
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

        import com.squareup.hephaestus.annotations.ContributesTo
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

        import com.squareup.hephaestus.annotations.ContributesTo
        $import
        
        $annotation(Any::class)
        interface ComponentInterface {
          @ContributesTo(Any::class)
          @dagger.Module
          abstract class InnerModule
        }
    """
    ) {
      assertThat(componentInterface.anyDaggerComponent.modules.toList())
          .containsExactly(innerModule.kotlin)
    }
  }

  private fun compile(
    source: String,
    block: Result.() -> Unit = { }
  ): Result = compile(source, skipAnalysis, block = block)

  private val Class<*>.anyDaggerComponent: AnyDaggerComponent
    get() = anyDaggerComponent(annotationClass)
}
