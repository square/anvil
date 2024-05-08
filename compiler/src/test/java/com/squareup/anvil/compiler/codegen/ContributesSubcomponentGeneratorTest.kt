package com.squareup.anvil.compiler.codegen

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.anvil.compiler.compile
import com.squareup.anvil.compiler.hintSubcomponent
import com.squareup.anvil.compiler.hintSubcomponentParentScope
import com.squareup.anvil.compiler.internal.testing.AnvilCompilationMode
import com.squareup.anvil.compiler.subcomponentInterface
import com.squareup.anvil.compiler.walkGeneratedFiles
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class ContributesSubcomponentGeneratorTest(
  private val mode: AnvilCompilationMode,
) {

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun data(): List<AnvilCompilationMode> = listOf(
      AnvilCompilationMode.Ksp(),
      AnvilCompilationMode.Embedded(),
    )
  }

  @Test fun `there is a hint for contributed subcomponents`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesSubcomponent

      @ContributesSubcomponent(Any::class, Unit::class)
      interface SubcomponentInterface
      """,
      mode = mode,
    ) {
      assertThat(subcomponentInterface.hintSubcomponent?.java).isEqualTo(subcomponentInterface)
      assertThat(subcomponentInterface.hintSubcomponentParentScope).isEqualTo(Unit::class)

      val generatedFile = walkGeneratedFiles(mode).single()

      assertThat(generatedFile.name)
        .isEqualTo("Com_squareup_test_SubcomponentInterface_7280b174.kt")
    }
  }

  @Test fun `there is a hint for contributed subcomponents - abstract class`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesSubcomponent

      @ContributesSubcomponent(Any::class, Unit::class)
      abstract class SubcomponentInterface
      """,
      mode = mode,
    ) {
      assertThat(subcomponentInterface.hintSubcomponent?.java).isEqualTo(subcomponentInterface)
      assertThat(subcomponentInterface.hintSubcomponentParentScope).isEqualTo(Unit::class)
    }
  }

  @Test fun `there is a hint for contributed subcomponents with a different parameter order`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesSubcomponent

      @ContributesSubcomponent(parentScope = Unit::class, scope = Any::class)
      interface SubcomponentInterface
      """,
      mode = mode,
    ) {
      assertThat(subcomponentInterface.hintSubcomponent?.java).isEqualTo(subcomponentInterface)
      assertThat(subcomponentInterface.hintSubcomponentParentScope).isEqualTo(Unit::class)
    }
  }

  @Test fun `there is a hint for contributed inner subcomponents`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesSubcomponent

      class Outer {
        @ContributesSubcomponent(Any::class, Unit::class)
        interface SubcomponentInterface
      }
      """,
      mode = mode,
    ) {
      val subcomponentInterface = classLoader
        .loadClass("com.squareup.test.Outer\$SubcomponentInterface")
      assertThat(subcomponentInterface.hintSubcomponent?.java).isEqualTo(subcomponentInterface)
      assertThat(subcomponentInterface.hintSubcomponentParentScope).isEqualTo(Unit::class)

      val generatedFile = walkGeneratedFiles(mode).single()

      assertThat(generatedFile.name)
        .isEqualTo("Com_squareup_test_Outer_SubcomponentInterface_c4e6e962.kt")
    }
  }

  @Test fun `there is a hint for contributed subcomponents with an interace factory`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesSubcomponent
      import com.squareup.anvil.annotations.ContributesSubcomponent.Factory

      @ContributesSubcomponent(Any::class, Unit::class)
      interface SubcomponentInterface {
        @Factory
        interface SubcomponentFactory {
          fun create(): SubcomponentInterface
        }
      }
      """,
      mode = mode,
    ) {
      assertThat(subcomponentInterface.hintSubcomponent?.java).isEqualTo(subcomponentInterface)
      assertThat(subcomponentInterface.hintSubcomponentParentScope).isEqualTo(Unit::class)

      val generatedFile = walkGeneratedFiles(mode).single()

      assertThat(generatedFile.name)
        .isEqualTo("Com_squareup_test_SubcomponentInterface_7280b174.kt")
    }
  }

  @Test fun `there is a hint for contributed subcomponents with an abstract class factory`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesSubcomponent
      import com.squareup.anvil.annotations.ContributesSubcomponent.Factory

      @ContributesSubcomponent(Any::class, Unit::class)
      interface SubcomponentInterface {
        @Factory
        abstract class SubcomponentFactory {
          abstract fun create(): SubcomponentInterface
        }
      }
      """,
      mode = mode,
    ) {
      assertThat(subcomponentInterface.hintSubcomponent?.java).isEqualTo(subcomponentInterface)
      assertThat(subcomponentInterface.hintSubcomponentParentScope).isEqualTo(Unit::class)

      val generatedFile = walkGeneratedFiles(mode).single()

      assertThat(generatedFile.name)
        .isEqualTo("Com_squareup_test_SubcomponentInterface_7280b174.kt")
    }
  }

  @Test fun `contributed subcomponents must be a interfaces or abstract classes`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesSubcomponent

      @ContributesSubcomponent(Any::class, Unit::class)
      class SubcomponentInterface
      """,
      mode = mode,
      expectExitCode = ExitCode.COMPILATION_ERROR,
    ) {
      // Position to the class.
      assertThat(messages).contains("Source0.kt:6:")
      assertThat(messages).contains(
        "com.squareup.test.SubcomponentInterface is annotated with @ContributesSubcomponent, " +
          "but this class is not an interface.",
      )
    }

    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesSubcomponent

      @ContributesSubcomponent(Any::class, Unit::class)
      object SubcomponentInterface
      """,
      mode = mode,
      expectExitCode = ExitCode.COMPILATION_ERROR,
    ) {
      // Position to the class.
      assertThat(messages).contains("Source0.kt:6:")
      assertThat(messages).contains(
        "com.squareup.test.SubcomponentInterface is annotated with @ContributesSubcomponent, " +
          "but this class is not an interface.",
      )
    }
  }

  @Test fun `contributed subcomponents must be public`() {
    val visibilities = setOf(
      "internal",
      "private",
      "protected",
    )

    visibilities.forEach { visibility ->
      compile(
        """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesSubcomponent
  
        @ContributesSubcomponent(Any::class, Unit::class)
        $visibility interface SubcomponentInterface
        """,
        mode = mode,
        expectExitCode = ExitCode.COMPILATION_ERROR,
      ) {
        // Position to the class.
        assertThat(messages).contains("Source0.kt:6:")
        assertThat(messages).contains(
          "com.squareup.test.SubcomponentInterface is contributed to the Dagger graph, but the " +
            "interface is not public. Only public interfaces are supported.",
        )
      }
    }
  }

  @Test
  fun `a contributed subcomponent is allowed to have a parent component that's contributed to the parent scope`() {
    compile(
      """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesSubcomponent
        import com.squareup.anvil.annotations.ContributesTo
  
        @ContributesSubcomponent(Any::class, parentScope = Unit::class)
        interface SubcomponentInterface {
          @ContributesTo(Unit::class)
          interface AnyParentComponent {
            fun createComponent(): SubcomponentInterface
          }
        }
      """,
      mode = mode,
    ) {
      val parentComponent = subcomponentInterface.parentComponentInterface
      assertThat(parentComponent).isNotNull()

      val annotation = parentComponent.getAnnotation(ContributesTo::class.java)
      assertThat(annotation).isNotNull()
      assertThat(annotation.scope).isEqualTo(Unit::class)
    }
  }

  @Test fun `two or more parent component interfaces aren't allowed`() {
    compile(
      """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesSubcomponent
        import com.squareup.anvil.annotations.ContributesTo
  
        @ContributesSubcomponent(Any::class, parentScope = Unit::class)
        interface SubcomponentInterface {
          @ContributesTo(Unit::class)
          interface AnyParentComponent1 {
            fun createComponent(): SubcomponentInterface
          }
          @ContributesTo(Unit::class)
          interface AnyParentComponent2 {
            fun createComponent(): SubcomponentInterface
          }
        }
      """,
      mode = mode,
      expectExitCode = ExitCode.COMPILATION_ERROR,
    ) {
      assertThat(messages).contains("Source0.kt:7:")
      assertThat(messages).contains(
        "Expected zero or one parent component interface within " +
          "com.squareup.test.SubcomponentInterface being contributed to the parent scope.",
      )
    }
  }

  @Test
  fun `a parent component interface must not have more than one function returning the subcomponent`() {
    compile(
      """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesSubcomponent
        import com.squareup.anvil.annotations.ContributesTo
  
        @ContributesSubcomponent(Any::class, parentScope = Unit::class)
        interface SubcomponentInterface {
          @ContributesTo(Unit::class)
          interface AnyParentComponent1 {
            fun createComponent1(): SubcomponentInterface
            fun createComponent2(): SubcomponentInterface
          }
        }
      """,
      mode = mode,
      expectExitCode = ExitCode.COMPILATION_ERROR,
    ) {
      assertThat(messages).contains("Source0.kt:9:")
      assertThat(messages).contains(
        "Expected zero or one function returning the subcomponent " +
          "com.squareup.test.SubcomponentInterface.",
      )
    }
  }

  @Test
  fun `there must be only one factory`() {
    compile(
      """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesSubcomponent
        import com.squareup.anvil.annotations.ContributesSubcomponent.Factory
        import com.squareup.anvil.annotations.ContributesTo
  
        @ContributesSubcomponent(Any::class, parentScope = Unit::class)
        interface SubcomponentInterface {
          @Factory
          interface ComponentFactory1 {
            fun createComponent(): SubcomponentInterface
          }
          @Factory
          interface ComponentFactory2 {
            fun createComponent(): SubcomponentInterface
          }
        }
      """,
      mode = mode,
      expectExitCode = ExitCode.COMPILATION_ERROR,
    ) {
      assertThat(messages).contains("Source0.kt:8:")
      assertThat(messages).contains(
        "Expected zero or one factory within com.squareup.test.SubcomponentInterface.",
      )
    }
  }

  @Test
  fun `a factory must be an abstract class or interface`() {
    compile(
      """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesSubcomponent
        import com.squareup.anvil.annotations.ContributesSubcomponent.Factory
        import com.squareup.anvil.annotations.ContributesTo
  
        @ContributesSubcomponent(Any::class, parentScope = Unit::class)
        interface SubcomponentInterface {
          @Factory
          object ComponentFactory {
            fun createComponent(): SubcomponentInterface = throw NotImplementedError()
          }
        }
      """,
      mode = mode,
      expectExitCode = ExitCode.COMPILATION_ERROR,
    ) {
      assertThat(messages).contains("Source0.kt:10:")
      assertThat(messages).contains("A factory must be an interface or an abstract class.")
    }

    compile(
      """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesSubcomponent
        import com.squareup.anvil.annotations.ContributesSubcomponent.Factory
        import com.squareup.anvil.annotations.ContributesTo
  
        @ContributesSubcomponent(Any::class, parentScope = Unit::class)
        interface SubcomponentInterface {
          @Factory
          class ComponentFactory {
            fun createComponent(): SubcomponentInterface = throw NotImplementedError()
          }
        }
      """,
      mode = mode,
      expectExitCode = ExitCode.COMPILATION_ERROR,
    ) {
      assertThat(messages).contains("Source0.kt:10:")
      assertThat(messages).contains("A factory must be an interface or an abstract class.")
    }
  }

  @Test
  fun `a factory must have a single abstract method returning the subcomponent - no function`() {
    compile(
      """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesSubcomponent
        import com.squareup.anvil.annotations.ContributesSubcomponent.Factory
        import com.squareup.anvil.annotations.ContributesTo
  
        @ContributesSubcomponent(Any::class, parentScope = Unit::class)
        interface SubcomponentInterface {
          @Factory
          interface ComponentFactory
        }
      """,
      mode = mode,
      expectExitCode = ExitCode.COMPILATION_ERROR,
    ) {
      assertThat(messages).contains("Source0.kt:10:")
      assertThat(messages).contains(
        "A factory must have exactly one abstract function returning the subcomponent " +
          "com.squareup.test.SubcomponentInterface.",
      )
    }
  }

  @Test
  fun `a factory must have a single abstract method returning the subcomponent - two functions`() {
    compile(
      """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesSubcomponent
        import com.squareup.anvil.annotations.ContributesSubcomponent.Factory
        import com.squareup.anvil.annotations.ContributesTo
  
        @ContributesSubcomponent(Any::class, parentScope = Unit::class)
        interface SubcomponentInterface {
          @Factory
          interface ComponentFactory {
            fun createComponent1(): SubcomponentInterface
            fun createComponent2(): SubcomponentInterface
          }
        }
      """,
      mode = mode,
      expectExitCode = ExitCode.COMPILATION_ERROR,
    ) {
      assertThat(messages).contains("Source0.kt:10:")
      assertThat(messages).contains(
        "A factory must have exactly one abstract function returning the subcomponent " +
          "com.squareup.test.SubcomponentInterface.",
      )
    }
  }

  @Test
  fun `a factory must have a single abstract method returning the subcomponent - non-abstract function`() {
    compile(
      """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesSubcomponent
        import com.squareup.anvil.annotations.ContributesSubcomponent.Factory
        import com.squareup.anvil.annotations.ContributesTo
  
        @ContributesSubcomponent(Any::class, parentScope = Unit::class)
        interface SubcomponentInterface {
          @Factory
          abstract class ComponentFactory {
            fun createComponent(): SubcomponentInterface = throw NotImplementedError()
          }
        }
      """,
      mode = mode,
      expectExitCode = ExitCode.COMPILATION_ERROR,
    ) {
      assertThat(messages).contains("Source0.kt:10:")
      assertThat(messages).contains(
        "A factory must have exactly one abstract function returning the subcomponent " +
          "com.squareup.test.SubcomponentInterface.",
      )
    }
  }

  @Test
  fun `using Dagger's @Subcomponent_Factory is an error`() {
    compile(
      """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesSubcomponent
        import dagger.Subcomponent
  
        @ContributesSubcomponent(Any::class, parentScope = Unit::class)
        interface SubcomponentInterface {
          @Subcomponent.Factory
          interface ComponentFactory {
            fun createComponent(): SubcomponentInterface
          }
        }
      """,
      mode = mode,
      expectExitCode = ExitCode.COMPILATION_ERROR,
    ) {
      assertThat(messages).contains("Source0.kt:9:")
      assertThat(messages).contains(
        "Within a class using @ContributesSubcomponent you must use " +
          "com.squareup.anvil.annotations.ContributesSubcomponent.Factory and not " +
          "dagger.Subcomponent.Factory.",
      )
    }
  }

  @Test
  fun `using Dagger's @Subcomponent_Builder is an error`() {
    compile(
      """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesSubcomponent
        import dagger.Subcomponent
  
        @ContributesSubcomponent(Any::class, parentScope = Unit::class)
        interface SubcomponentInterface {
          @Subcomponent.Builder
          interface ComponentFactory {
            fun createComponent(): SubcomponentInterface
          }
        }
      """,
      mode = mode,
      expectExitCode = ExitCode.COMPILATION_ERROR,
    ) {
      assertThat(messages).contains("Source0.kt:9:")
      assertThat(messages).contains(
        "Within a class using @ContributesSubcomponent you must use " +
          "com.squareup.anvil.annotations.ContributesSubcomponent.Factory and not " +
          "dagger.Subcomponent.Builder. Builders aren't supported.",
      )
    }
  }

  @Test fun `a contributed subcomponent can be configured to replace another subcomponent`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesSubcomponent

      @ContributesSubcomponent(
        scope = Any::class, 
        parentScope = Unit::class
      )
      interface SubcomponentInterface1

      @ContributesSubcomponent(
        scope = Any::class, 
        parentScope = Int::class,
        replaces = [SubcomponentInterface1::class]
      )
      interface SubcomponentInterface2
      """,
      mode = mode,
    ) {
      assertThat(subcomponentInterface1.hintSubcomponent?.java).isEqualTo(subcomponentInterface1)
      assertThat(subcomponentInterface1.hintSubcomponentParentScope).isEqualTo(Unit::class)

      assertThat(subcomponentInterface2.hintSubcomponent?.java).isEqualTo(subcomponentInterface2)
      assertThat(subcomponentInterface2.hintSubcomponentParentScope).isEqualTo(Int::class)
    }
  }

  @Test fun `a replaced subcomponent must use the same scope`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesSubcomponent

      @ContributesSubcomponent(
        scope = Long::class, 
        parentScope = Unit::class
      )
      interface SubcomponentInterface1

      @ContributesSubcomponent(
        scope = Any::class, 
        parentScope = Int::class,
        replaces = [SubcomponentInterface1::class]
      )
      interface SubcomponentInterface2
      """,
      mode = mode,
      expectExitCode = ExitCode.COMPILATION_ERROR,
    ) {
      assertThat(messages).contains("Source0.kt:16:")
      assertThat(messages).contains(
        "com.squareup.test.SubcomponentInterface2 with scope kotlin.Any wants to replace " +
          "com.squareup.test.SubcomponentInterface1 with scope kotlin.Long. The replacement " +
          "must use the same scope.",
      )
    }
  }

  private val Class<*>.parentComponentInterface: Class<*>
    get() = classLoader.loadClass("$canonicalName\$AnyParentComponent")

  private val JvmCompilationResult.subcomponentInterface1: Class<*>
    get() = classLoader.loadClass("com.squareup.test.SubcomponentInterface1")

  private val JvmCompilationResult.subcomponentInterface2: Class<*>
    get() = classLoader.loadClass("com.squareup.test.SubcomponentInterface2")
}
