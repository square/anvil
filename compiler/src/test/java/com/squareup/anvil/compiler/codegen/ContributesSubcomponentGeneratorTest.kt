package com.squareup.anvil.compiler.codegen

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.anvil.compiler.compile
import com.squareup.anvil.compiler.hintSubcomponent
import com.squareup.anvil.compiler.hintSubcomponentParentScope
import com.squareup.anvil.compiler.isError
import com.squareup.anvil.compiler.subcomponentInterface
import com.tschuchort.compiletesting.KotlinCompilation.Result
import org.junit.Test
import java.io.File

class ContributesSubcomponentGeneratorTest {

  @Test fun `there is a hint for contributed subcomponents`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesSubcomponent

      @ContributesSubcomponent(Any::class, Unit::class)
      interface SubcomponentInterface
      """
    ) {
      assertThat(subcomponentInterface.hintSubcomponent?.java).isEqualTo(subcomponentInterface)
      assertThat(subcomponentInterface.hintSubcomponentParentScope).isEqualTo(Unit::class)

      val generatedFile = File(outputDirectory.parent, "build/anvil")
        .walk()
        .single { it.isFile && it.extension == "kt" }

      assertThat(generatedFile.name).isEqualTo("SubcomponentInterface.kt")
    }
  }

  @Test fun `there is a hint for contributed subcomponents - abstract class`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesSubcomponent

      @ContributesSubcomponent(Any::class, Unit::class)
      abstract class SubcomponentInterface
      """
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
      """
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
      """
    ) {
      val subcomponentInterface = classLoader
        .loadClass("com.squareup.test.Outer\$SubcomponentInterface")
      assertThat(subcomponentInterface.hintSubcomponent?.java).isEqualTo(subcomponentInterface)
      assertThat(subcomponentInterface.hintSubcomponentParentScope).isEqualTo(Unit::class)

      val generatedFile = File(outputDirectory.parent, "build/anvil")
        .walk()
        .single { it.isFile && it.extension == "kt" }

      assertThat(generatedFile.name).isEqualTo("Outer_SubcomponentInterface.kt")
    }
  }

  @Test fun `contributed subcomponents must be a interfaces or abstract classes`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesSubcomponent

      @ContributesSubcomponent(Any::class, Unit::class)
      class SubcomponentInterface
      """
    ) {
      assertThat(exitCode).isError()
      // Position to the class.
      assertThat(messages).contains("Source0.kt: (6,")
      assertThat(messages).contains(
        "com.squareup.test.SubcomponentInterface is annotated with @ContributesSubcomponent, " +
          "but this class is not an interface."
      )
    }

    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesSubcomponent

      @ContributesSubcomponent(Any::class, Unit::class)
      object SubcomponentInterface
      """
    ) {
      assertThat(exitCode).isError()
      // Position to the class.
      assertThat(messages).contains("Source0.kt: (6,")
      assertThat(messages).contains(
        "com.squareup.test.SubcomponentInterface is annotated with @ContributesSubcomponent, " +
          "but this class is not an interface."
      )
    }
  }

  @Test fun `contributed subcomponents must be public`() {
    val visibilities = setOf(
      "internal",
      "private",
      "protected"
    )

    visibilities.forEach { visibility ->
      compile(
        """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesSubcomponent
  
        @ContributesSubcomponent(Any::class, Unit::class)
        $visibility interface SubcomponentInterface
        """
      ) {
        assertThat(exitCode).isError()
        // Position to the class.
        assertThat(messages).contains("Source0.kt: (6, ")
        assertThat(messages).contains(
          "com.squareup.test.SubcomponentInterface is contributed to the Dagger graph, but the " +
            "interface is not public. Only public interfaces are supported."
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
      """
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
      """
    ) {
      assertThat(exitCode).isError()
      assertThat(messages).contains("Source0.kt: (6, 1)")
      assertThat(messages).contains(
        "Expected zero or one parent component interface within " +
          "com.squareup.test.SubcomponentInterface being contributed to the parent scope."
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
      """
    ) {
      assertThat(exitCode).isError()
      assertThat(messages).contains("Source0.kt: (6, 1)")
      assertThat(messages).contains(
        "Expected zero or one function returning the subcomponent " +
          "com.squareup.test.SubcomponentInterface."
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
      """
    ) {
      assertThat(exitCode).isError()
      assertThat(messages).contains("Source0.kt: (7, 1)")
      assertThat(messages).contains(
        "Expected zero or one factory within com.squareup.test.SubcomponentInterface."
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
      """
    ) {
      assertThat(exitCode).isError()
      assertThat(messages).contains("Source0.kt: (9, 3)")
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
      """
    ) {
      assertThat(exitCode).isError()
      assertThat(messages).contains("Source0.kt: (9, 3)")
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
      """
    ) {
      assertThat(exitCode).isError()
      assertThat(messages).contains("Source0.kt: (9, 3)")
      assertThat(messages).contains(
        "A factory must have exactly one abstract function returning the subcomponent " +
          "com.squareup.test.SubcomponentInterface."
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
      """
    ) {
      assertThat(exitCode).isError()
      assertThat(messages).contains("Source0.kt: (9, 3)")
      assertThat(messages).contains(
        "A factory must have exactly one abstract function returning the subcomponent " +
          "com.squareup.test.SubcomponentInterface."
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
      """
    ) {
      assertThat(exitCode).isError()
      assertThat(messages).contains("Source0.kt: (9, 3)")
      assertThat(messages).contains(
        "A factory must have exactly one abstract function returning the subcomponent " +
          "com.squareup.test.SubcomponentInterface."
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
      """
    ) {
      assertThat(exitCode).isError()
      assertThat(messages).contains("Source0.kt: (8, 3)")
      assertThat(messages).contains(
        "Within a class using @ContributesSubcomponent you must use " +
          "com.squareup.anvil.annotations.ContributesSubcomponent.Factory and not " +
          "dagger.Subcomponent.Factory."
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
      """
    ) {
      assertThat(exitCode).isError()
      assertThat(messages).contains("Source0.kt: (8, 3)")
      assertThat(messages).contains(
        "Within a class using @ContributesSubcomponent you must use " +
          "com.squareup.anvil.annotations.ContributesSubcomponent.Factory and not " +
          "dagger.Subcomponent.Builder. Builders aren't supported."
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
      """
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
      """
    ) {
      assertThat(exitCode).isError()
      assertThat(messages).contains("Source0.kt: (11, 1)")
      assertThat(messages).contains(
        "com.squareup.test.SubcomponentInterface2 with scope kotlin.Any wants to replace " +
          "com.squareup.test.SubcomponentInterface1 with scope kotlin.Long. The replacement " +
          "must use the same scope."
      )
    }
  }

  private val Class<*>.parentComponentInterface: Class<*>
    get() = classLoader.loadClass("$canonicalName\$AnyParentComponent")

  private val Result.subcomponentInterface1: Class<*>
    get() = classLoader.loadClass("com.squareup.test.SubcomponentInterface1")

  private val Result.subcomponentInterface2: Class<*>
    get() = classLoader.loadClass("com.squareup.test.SubcomponentInterface2")
}
