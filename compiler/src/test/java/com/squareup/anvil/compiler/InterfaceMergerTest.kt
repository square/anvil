package com.squareup.anvil.compiler

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.annotations.MergeComponent
import com.squareup.anvil.annotations.MergeSubcomponent
import com.squareup.anvil.annotations.compat.MergeInterfaces
import com.squareup.anvil.compiler.internal.testing.AnvilCompilation
import com.squareup.anvil.compiler.internal.testing.extends
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.OK
import org.junit.jupiter.api.TestFactory

class InterfaceMergerTest : AnnotationsTest(
  MergeComponent::class,
  MergeSubcomponent::class,
  MergeInterfaces::class,
) {

  @TestFactory
  fun `interfaces are merged successfully`() = testFactory {
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesTo
      $import
      
      @ContributesTo(Any::class)
      interface ContributingInterface
      
      @ContributesTo(Any::class)
      interface SecondContributingInterface
      
      $annotation(Any::class)
      interface ComponentInterface
      """,
    ) {
      assertThat(componentInterface extends contributingInterface).isTrue()
      assertThat(componentInterface extends secondContributingInterface).isTrue()
    }
  }

  @TestFactory
  fun `parent interface is merged`() = testFactory {
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesTo
      $import
      
      interface ParentInterface
      
      @ContributesTo(Any::class)
      interface ContributingInterface : ParentInterface
      
      $annotation(Any::class)
      interface ComponentInterface
      """,
    ) {
      assertThat(componentInterface extends parentInterface).isTrue()
    }
  }

  @TestFactory
  fun `interfaces are not merged without @Merge annotation`() = testFactory {
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesTo
      $import
      
      @ContributesTo(Any::class)
      interface ContributingInterface
      
      interface ComponentInterface
      """,
    ) {
      assertThat(componentInterface extends contributingInterface).isFalse()
    }
  }

  @TestFactory
  fun `interfaces are not merged without @ContributesTo annotation`() = testFactory {
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesTo
      $import
      
      interface ContributingInterface
      
      $annotation(Any::class)
      interface ComponentInterface
      """,
    ) {
      assertThat(componentInterface extends contributingInterface).isFalse()
    }
  }

  @TestFactory
  fun `code can be in any package`() = testFactory {
    compile(
      """
      package com.other
      
      import com.squareup.anvil.annotations.ContributesTo
      $import
      
      @ContributesTo(Any::class)
      interface ContributingInterface
      
      $annotation(Any::class)
      interface ComponentInterface
      """,
    ) {
      assertThat(
        classLoader.loadClass("com.other.ComponentInterface") extends
          classLoader.loadClass("com.other.ContributingInterface"),
      ).isTrue()
    }
  }

  @TestFactory
  fun `classes annotated with @MergeComponent must be interfaces`() = testFactory {
    compile(
      """
      package com.squareup.test
      
      $import
      
      $annotation(Any::class)
      abstract class MergingClass
      """,
    ) {
      assertThat(exitCode).isError()
      // Position to the class.
      assertThat(messages).contains("Source0.kt:6:16")
    }
  }

  @TestFactory
  fun `a contributed interface can be replaced`() = testFactory {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesTo
      $import
      
      @ContributesTo(Any::class)
      interface ContributingInterface
      
      @ContributesTo(
          Any::class,
          replaces = [ContributingInterface::class]
      )
      interface SecondContributingInterface

      $annotation(Any::class)
      interface ComponentInterface
      """,
    ) {
      assertThat(componentInterface extends contributingInterface).isFalse()
      assertThat(componentInterface extends secondContributingInterface).isTrue()
    }
  }

  @TestFactory
  fun `replaced interfaces must be interfaces and not classes`() = testFactory {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesTo
      $import
      
      @ContributesTo(Any::class)
      class ContributingInterface
      
      @ContributesTo(
          Any::class,
          replaces = [ContributingInterface::class]
      )
      interface SecondContributingInterface

      $annotation(Any::class)
      interface ComponentInterface
      """,
    ) {
      assertThat(exitCode).isError()
      // Position to the class. Unfortunately, a different error is reported that the class is
      // missing an @Module annotation.
      assertThat(messages).contains("Source0.kt:7:7")
    }
  }

  @TestFactory
  fun `replaced interfaces must use the same scope`() = testFactory {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesTo
      $import
      
      @ContributesTo(Unit::class)
      @ContributesTo(Int::class)
      interface ContributingInterface
      
      @ContributesTo(
          Any::class,
          replaces = [ContributingInterface::class]
      )
      interface SecondContributingInterface

      $annotation(Any::class)
      interface ComponentInterface
      """,
    ) {
      assertThat(exitCode).isError()
      // Position to the class. Unfortunately, a different error is reported that the class is
      // missing an @Module annotation.
      assertThat(messages).contains("Source0.kt:14:11")
      assertThat(messages).contains(
        "com.squareup.test.SecondContributingInterface with scopes [kotlin.Any] wants to replace " +
          "com.squareup.test.ContributingInterface, but the replaced class isn't contributed " +
          "to the same scope.",
      )
    }
  }

  @TestFactory
  fun `predefined interfaces are not replaced`() = testFactory {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesTo
      $import

      @ContributesTo(Any::class)
      interface ContributingInterface
      
      @ContributesTo(
          Any::class,
          replaces = [ContributingInterface::class]
      )
      interface SecondContributingInterface

      $annotation(Any::class)
      interface ComponentInterface : ContributingInterface
      """,
    ) {
      assertThat(componentInterface extends contributingInterface).isTrue()
      assertThat(componentInterface extends secondContributingInterface).isTrue()
    }
  }

  @TestFactory
  fun `interface can be excluded`() = testFactory {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesTo
      $import

      @ContributesTo(Any::class)
      interface ContributingInterface
      
      @ContributesTo(Any::class)
      interface SecondContributingInterface

      $annotation(
          scope = Any::class,
          exclude = [
            ContributingInterface::class
          ]
      )
      interface ComponentInterface
      """,
    ) {
      assertThat(componentInterface extends contributingInterface).isFalse()
      assertThat(componentInterface extends secondContributingInterface).isTrue()
    }
  }

  @TestFactory
  fun `excluded interfaces must use the same scope`() = testFactory {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesTo
      $import

      @ContributesTo(Unit::class)
      interface ContributingInterface
      
      @ContributesTo(Any::class)
      interface SecondContributingInterface

      $annotation(
          scope = Any::class,
          exclude = [
            ContributingInterface::class
          ]
      )
      interface ComponentInterface
      """,
    ) {
      assertThat(exitCode).isError()
      // Position to the class.
      assertThat(messages).contains("Source0.kt:18:11")
      assertThat(messages).contains(
        "com.squareup.test.ComponentInterface with scopes [kotlin.Any] wants to exclude " +
          "com.squareup.test.ContributingInterface, but the excluded class isn't contributed " +
          "to the same scope.",
      )
    }
  }

  @TestFactory
  fun `predefined interfaces cannot be excluded`() = testFactory {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesTo
      $import

      @ContributesTo(Any::class)
      interface ContributingInterface
      
      @ContributesTo(Any::class)
      interface SecondContributingInterface
      
      interface OtherInterface : SecondContributingInterface

      $annotation(
          scope = Any::class,
          exclude = [
            ContributingInterface::class,
            SecondContributingInterface::class
          ]
      )
      interface ComponentInterface : ContributingInterface, OtherInterface
      """,
    ) {
      assertThat(exitCode).isError()
      // Position to the class.
      assertThat(messages).contains(
        "ComponentInterface excludes types that it implements or extends. These types cannot " +
          "be excluded. Look at all the super types to find these classes: " +
          "com.squareup.test.ContributingInterface, " +
          "com.squareup.test.SecondContributingInterface",
      )
    }
  }

  @TestFactory
  fun `interfaces are added to components with corresponding scope`() = testFactory {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesTo
      $import

      @ContributesTo(Any::class)
      interface ContributingInterface
      
      @ContributesTo(Unit::class)
      interface SecondContributingInterface

      $annotation(Any::class)
      interface ComponentInterface
      
      $annotation(Unit::class)
      interface SubcomponentInterface
      """,
    ) {
      assertThat(componentInterface extends contributingInterface).isTrue()
      assertThat(componentInterface extends secondContributingInterface).isFalse()

      assertThat(subcomponentInterface extends contributingInterface).isFalse()
      assertThat(subcomponentInterface extends secondContributingInterface).isTrue()
    }
  }

  @TestFactory
  fun `interfaces are added to components with corresponding scope and component type`() =
    testFactory {
      assumeMergeComponent(annotationClass)

      compile(
        """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesTo
      import com.squareup.anvil.annotations.MergeComponent
      import com.squareup.anvil.annotations.MergeSubcomponent

      @ContributesTo(Any::class)
      interface ContributingInterface
      
      @ContributesTo(Unit::class)
      interface SecondContributingInterface

      @MergeComponent(Any::class)
      interface ComponentInterface
      
      @MergeSubcomponent(Unit::class)
      interface SubcomponentInterface
      """,
      ) {
        assertThat(componentInterface extends contributingInterface).isTrue()
        assertThat(componentInterface extends secondContributingInterface).isFalse()

        assertThat(subcomponentInterface extends contributingInterface).isFalse()
        assertThat(subcomponentInterface extends secondContributingInterface).isTrue()
      }
    }

  @TestFactory
  fun `contributed interfaces must be public`() = testFactory {
    val visibilities = setOf(
      "internal",
      "private",
      "protected",
    )

    visibilities.forEach { visibility ->
      compile(
        """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesTo
        $import
  
        @ContributesTo(Any::class)
        $visibility interface ContributingInterface
        
        $annotation(Any::class)
        interface ComponentInterface
        """,
      ) {
        assertThat(exitCode).isError()
        // Position to the class.
        assertThat(messages).contains("Source0.kt:7")
      }
    }
  }

  @TestFactory
  fun `inner interfaces are merged`() = testFactory {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesTo
      $import

      class SomeClass {
        @ContributesTo(Any::class)
        interface InnerInterface
      }
      
      $annotation(Any::class)
      interface ComponentInterface
      """,
    ) {
      assertThat(componentInterface extends innerInterface).isTrue()
    }
  }

  @TestFactory
  fun `inner interface in a merged component with different scope are merged`() = testFactory {
    assumeMergeComponent(annotationClass)

    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesTo
      import com.squareup.anvil.annotations.MergeComponent
      import com.squareup.anvil.annotations.MergeSubcomponent
      
      @MergeComponent(Unit::class)
      interface ComponentInterface
      
      @MergeSubcomponent(Any::class)
      interface SubcomponentInterface {
        @ContributesTo(Unit::class)
        interface InnerInterface
      }
      """,
    ) {
      val innerInterface = classLoader
        .loadClass("com.squareup.test.SubcomponentInterface\$InnerInterface")
      assertThat(componentInterface extends innerInterface).isTrue()
      assertThat(componentInterface.interfaces).hasLength(1)
      assertThat(subcomponentInterface.interfaces).hasLength(0)
    }
  }

  @TestFactory
  fun `interfaces are merged without a package`() = testFactory {
    compile(
      """
      import com.squareup.anvil.annotations.ContributesTo
      $import
      
      @ContributesTo(Any::class)
      interface ContributingInterface
      
      $annotation(Any::class)
      interface ComponentInterface
      """,
    ) {
      val componentInterface = classLoader.loadClass("ComponentInterface")
      val contributingInterface = classLoader.loadClass("ContributingInterface")
      assertThat(componentInterface extends contributingInterface).isTrue()
    }
  }

  @TestFactory
  fun `module interfaces are not merged`() = testFactory {
    // They could cause errors while compiling code when adding our contributed super classes.
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesTo
      $import
      
      @ContributesTo(Any::class)
      interface ContributingInterface
      
      @dagger.Module
      @ContributesTo(Any::class)
      interface SecondContributingInterface
      
      $annotation(Any::class)
      interface ComponentInterface
      """,
    ) {
      assertThat(componentInterface extends contributingInterface).isTrue()
      assertThat(componentInterface extends secondContributingInterface).isFalse()
    }
  }

  @TestFactory
  fun `merged interfaces can be contributed to another scope at the same time`() = testFactory {
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesTo
      $import
      
      @ContributesTo(Unit::class)
      interface ContributingInterface
      
      @ContributesTo(Any::class)
      @com.squareup.anvil.annotations.compat.MergeInterfaces(Unit::class)
      interface SecondContributingInterface
      
      $annotation(Any::class)
      interface ComponentInterface
      """,
    ) {
      assertThat(secondContributingInterface extends contributingInterface).isTrue()

      assertThat(componentInterface extends contributingInterface).isTrue()
      assertThat(componentInterface extends secondContributingInterface).isTrue()
    }
  }

  @TestFactory
  fun `anonymous inner classes should not be evaluated`() = testFactory {
    compile(
      """
      package com.squareup.test

      class NonContributingClass {
        private val anonymousDoSomething = object : DoSomethingInterface {
          override fun doSomething() = Unit
        }
      }
      
      interface DoSomethingInterface {
        fun doSomething()
      }
      """,
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }

  @TestFactory
  fun `interfaces contributed to multiple scopes are merged successfully`() = testFactory {
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesTo
      $import
      
      @ContributesTo(Any::class)
      @ContributesTo(Unit::class)
      interface ContributingInterface
      
      @ContributesTo(Any::class)
      interface SecondContributingInterface
      
      $annotation(Any::class)
      interface ComponentInterface
      
      $annotation(Unit::class)
      interface SubcomponentInterface
      """,
    ) {
      assertThat(componentInterface extends contributingInterface).isTrue()
      assertThat(componentInterface extends secondContributingInterface).isTrue()
      assertThat(subcomponentInterface extends contributingInterface).isTrue()
      assertThat(subcomponentInterface extends secondContributingInterface).isFalse()
    }
  }

  @TestFactory
  fun `interfaces contributed to multiple scopes are merged successfully with multiple compilations`() =
    testFactory {
      val firstResult = compile(
        """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesTo

      @ContributesTo(Any::class)
      @ContributesTo(Unit::class)
      interface ContributingInterface
      """,
        workingDir = workingDir.resolve("first"),
      ) {
        assertThat(exitCode).isEqualTo(OK)
      }

      compile(
        """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesTo
      $import
      
      @ContributesTo(Any::class)
      interface SecondContributingInterface
      
      $annotation(Any::class)
      interface ComponentInterface
      
      $annotation(Unit::class)
      interface SubcomponentInterface
      """,
        previousCompilationResult = firstResult,
        workingDir = workingDir.resolve("second"),
      ) {
        assertThat(componentInterface extends contributingInterface).isTrue()
        assertThat(componentInterface extends secondContributingInterface).isTrue()
        assertThat(subcomponentInterface extends contributingInterface).isTrue()
        assertThat(subcomponentInterface extends secondContributingInterface).isFalse()
      }
    }

  @TestFactory
  fun `interfaces contributed to multiple scopes can be replaced`() = testFactory {
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesTo
      $import
      
      @ContributesTo(Any::class)
      @ContributesTo(Unit::class)
      interface ContributingInterface
      
      @ContributesTo(Any::class, replaces = [ContributingInterface::class])
      interface SecondContributingInterface
      
      $annotation(Any::class)
      interface ComponentInterface
      
      $annotation(Unit::class)
      interface SubcomponentInterface
      """,
    ) {
      assertThat(componentInterface extends contributingInterface).isFalse()
      assertThat(componentInterface extends secondContributingInterface).isTrue()
      assertThat(subcomponentInterface extends contributingInterface).isTrue()
      assertThat(subcomponentInterface extends secondContributingInterface).isFalse()
    }
  }

  @TestFactory
  fun `replaced interfaces contributed to multiple scopes must use the same scope`() = testFactory {
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesTo
      $import
      
      @ContributesTo(Any::class)
      @ContributesTo(Unit::class)
      interface ContributingInterface
      
      @ContributesTo(Int::class, replaces = [ContributingInterface::class])
      interface SecondContributingInterface
      
      $annotation(Any::class)
      interface ComponentInterface
      
      $annotation(Unit::class)
      interface SubcomponentInterface1
      
      $annotation(Int::class)
      interface SubcomponentInterface2
      """,
    ) {
      assertThat(exitCode).isError()
      assertThat(messages).contains(
        "com.squareup.test.SecondContributingInterface with scopes [kotlin.Int] wants to " +
          "replace com.squareup.test.ContributingInterface, but the replaced class isn't " +
          "contributed to the same scope.",
      )
    }
  }

  @TestFactory
  fun `interfaces contributed to multiple scopes can be excluded in one scope`() = testFactory {
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesTo
      $import
      
      @ContributesTo(Any::class)
      @ContributesTo(Unit::class)
      interface ContributingInterface
      
      @ContributesTo(Any::class)
      interface SecondContributingInterface
      
      $annotation(Any::class, exclude = [ContributingInterface::class])
      interface ComponentInterface
      
      $annotation(Unit::class)
      interface SubcomponentInterface
      """,
    ) {
      assertThat(componentInterface extends contributingInterface).isFalse()
      assertThat(componentInterface extends secondContributingInterface).isTrue()
      assertThat(subcomponentInterface extends contributingInterface).isTrue()
      assertThat(subcomponentInterface extends secondContributingInterface).isFalse()
    }
  }

  @TestFactory
  fun `contributed interfaces in the old format are picked up`() = testFactory {
    val result = AnvilCompilation()
      .configureAnvil(enableAnvil = false)
      .compile(
        """
        package com.squareup.test
      
        import com.squareup.anvil.annotations.ContributesTo
        $import
        
        @ContributesTo(Any::class)
        interface ContributingInterface  
        """,
        """
        package anvil.hint.merge.com.squareup.test

        import com.squareup.test.ContributingInterface
        import kotlin.reflect.KClass
        
        public val com_squareup_test_ContributingInterface_reference: KClass<ContributingInterface> = ContributingInterface::class
        
        // Note that the number is missing after the scope. 
        public val com_squareup_test_ContributingInterface_scope: KClass<Any> = Any::class
        """.trimIndent(),
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
      previousCompilationResult = result,
    ) {
      assertThat(componentInterface extends contributingInterface).isTrue()
    }
  }
}
