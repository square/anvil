package com.squareup.anvil.compiler

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.annotations.MergeComponent
import com.squareup.anvil.annotations.MergeSubcomponent
import com.squareup.anvil.annotations.compat.MergeInterfaces
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.COMPILATION_ERROR
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.INTERNAL_ERROR
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import kotlin.reflect.KClass

@RunWith(Parameterized::class)
class InterfaceMergerTest(
  private val annotationClass: KClass<*>
) {

  private val annotation = "@${annotationClass.simpleName}"
  private val import = "import ${annotationClass.java.canonicalName}"

  companion object {
    @Parameters(name = "{0}")
    @JvmStatic fun annotationClasses(): Collection<Any> {
      return listOf(MergeComponent::class, MergeSubcomponent::class, MergeInterfaces::class)
    }
  }

  @Test fun `interfaces are merged successfully`() {
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
        """
    ) {
      assertThat(componentInterface extends contributingInterface).isTrue()
      assertThat(componentInterface extends secondContributingInterface).isTrue()
    }
  }

  @Test fun `parent interface is merged`() {
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
        """
    ) {
      assertThat(componentInterface extends parentInterface).isTrue()
    }
  }

  @Test fun `interfaces are not merged without @Merge annotation`() {
    compile(
        """
        package com.squareup.test
        
        import com.squareup.anvil.annotations.ContributesTo
        $import
        
        @ContributesTo(Any::class)
        interface ContributingInterface
        
        interface ComponentInterface
        """
    ) {
      assertThat(componentInterface extends contributingInterface).isFalse()
    }
  }

  @Test fun `interfaces are not merged without @ContributesTo annotation`() {
    compile(
        """
        package com.squareup.test
        
        import com.squareup.anvil.annotations.ContributesTo
        $import
        
        interface ContributingInterface
        
        $annotation(Any::class)
        interface ComponentInterface
        """
    ) {
      assertThat(componentInterface extends contributingInterface).isFalse()
    }
  }

  @Test fun `code can be in any package`() {
    compile(
        """
        package com.other
        
        import com.squareup.anvil.annotations.ContributesTo
        $import
        
        @ContributesTo(Any::class)
        interface ContributingInterface
        
        $annotation(Any::class)
        interface ComponentInterface
        """
    ) {
      assertThat(
          classLoader.loadClass("com.other.ComponentInterface") extends
              classLoader.loadClass("com.other.ContributingInterface")
      ).isTrue()
    }
  }

  @Test fun `classes annotated with @MergeComponent must be interfaces`() {
    compile(
        """
        package com.squareup.test
        
        $import
        
        $annotation(Any::class)
        abstract class MergingClass
        """
    ) {
      assertThat(exitCode).isEqualTo(COMPILATION_ERROR)
      // Position to the class.
      assertThat(messages).contains("Source.kt: (6, 16)")
    }
  }

  @Test fun `a contributed interface can be replaced`() {
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
        """
    ) {
      assertThat(componentInterface extends contributingInterface).isFalse()
      assertThat(componentInterface extends secondContributingInterface).isTrue()
    }
  }

  @Test fun `replaced interfaces must be interfaces and not classes`() {
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
        """
    ) {
      assertThat(exitCode).isEqualTo(COMPILATION_ERROR)
      // Position to the class. Unfortunately, a different error is reported that the class is
      // missing an @Module annotation.
      assertThat(messages).contains("Source.kt: (7, 7)")
    }
  }

  @Test fun `replaced interfaces must use the same scope`() {
    compile(
        """
        package com.squareup.test

        import com.squareup.anvil.annotations.ContributesTo
        $import
        
        @ContributesTo(Unit::class)
        interface ContributingInterface
        
        @ContributesTo(
            Any::class,
            replaces = [ContributingInterface::class]
        )
        interface SecondContributingInterface        

        $annotation(Any::class)
        interface ComponentInterface
        """
    ) {
      assertThat(exitCode).isEqualTo(COMPILATION_ERROR)
      // Position to the class. Unfortunately, a different error is reported that the class is
      // missing an @Module annotation.
      assertThat(messages).contains("Source.kt: (13, 11)")
      assertThat(messages).contains(
          "com.squareup.test.SecondContributingInterface with scope kotlin.Any wants to replace " +
              "com.squareup.test.ContributingInterface with scope kotlin.Unit. The replacement " +
              "must use the same scope."
      )
    }
  }

  @Test fun `predefined interfaces are not replaced`() {
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
        """
    ) {
      assertThat(componentInterface extends contributingInterface).isTrue()
      assertThat(componentInterface extends secondContributingInterface).isTrue()
    }
  }

  @Test fun `interface can be excluded`() {
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
        """
    ) {
      assertThat(componentInterface extends contributingInterface).isFalse()
      assertThat(componentInterface extends secondContributingInterface).isTrue()
    }
  }

  @Test fun `excluded interfaces must use the same scope`() {
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
        """
    ) {
      assertThat(exitCode).isEqualTo(COMPILATION_ERROR)
      // Position to the class.
      assertThat(messages).contains("Source.kt: (18, 11)")
      assertThat(messages).contains(
          "com.squareup.test.ComponentInterface with scope kotlin.Any wants to exclude " +
              "com.squareup.test.ContributingInterface with scope kotlin.Unit. The exclusion " +
              "must use the same scope."
      )
    }
  }

  @Test fun `predefined interfaces cannot be excluded`() {
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
        """
    ) {
      assertThat(exitCode).isEqualTo(COMPILATION_ERROR)
      // Position to the class.
      assertThat(messages).contains(
          "ComponentInterface excludes types that it implements or extends. These types cannot " +
              "be excluded. Look at all the super types to find these classes: " +
              "com.squareup.test.ContributingInterface, " +
              "com.squareup.test.SecondContributingInterface"
      )
    }
  }

  @Test fun `interfaces are added to components with corresponding scope`() {
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
        """
    ) {
      assertThat(componentInterface extends contributingInterface).isTrue()
      assertThat(componentInterface extends secondContributingInterface).isFalse()

      assertThat(subcomponentInterface extends contributingInterface).isFalse()
      assertThat(subcomponentInterface extends secondContributingInterface).isTrue()
    }
  }

  @Test fun `interfaces are added to components with corresponding scope and component type`() {
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
        """
    ) {
      assertThat(componentInterface extends contributingInterface).isTrue()
      assertThat(componentInterface extends secondContributingInterface).isFalse()

      assertThat(subcomponentInterface extends contributingInterface).isFalse()
      assertThat(subcomponentInterface extends secondContributingInterface).isTrue()
    }
  }

  @Test fun `contributed interfaces must be public`() {
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
          $visibility interface ContributingInterface
          
          $annotation(Any::class)
          interface ComponentInterface
          """
      ) {
        assertThat(exitCode).isEqualTo(COMPILATION_ERROR)
        // Position to the class.
        assertThat(messages).contains("Source.kt: (7, ")
      }
    }
  }

  @Test fun `inner interfaces are merged`() {
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
        """
    ) {
      assertThat(componentInterface extends innerInterface).isTrue()
    }
  }

  @Test fun `inner interfaces in merged component fail`() {
    // They could cause errors while compiling code when adding our contributed super classes.
    compile(
        """
        package com.squareup.test

        import com.squareup.anvil.annotations.ContributesTo
        $import
        
        $annotation(Any::class)
        interface ComponentInterface {
          @ContributesTo(Any::class)
          interface InnerInterface
        }
        """
    ) {
      assertThat(exitCode).isEqualTo(INTERNAL_ERROR)
      // Position to the class.
      assertThat(messages).contains(
          "org.jetbrains.kotlin.util.KotlinFrontEndException: " +
              "Exception while analyzing expression at (8,18)"
      )
    }
  }

  @Test fun `inner modules in merged component fail`() {
    // They could cause errors while compiling code when adding our contributed super classes.
    compile(
        """
        package com.squareup.test

        import com.squareup.anvil.annotations.ContributesTo
        $import
        
        $annotation(Any::class, modules = [ComponentInterface.InnerModule::class])
        interface ComponentInterface {
          
          @dagger.Module
          abstract class InnerModule
        }
        """
    ) {
      assertThat(exitCode).isEqualTo(INTERNAL_ERROR)
      // Position to the class.
      assertThat(messages).contains(
          "org.jetbrains.kotlin.util.KotlinFrontEndException: " +
              "Exception while analyzing expression at (6,4"
      )
    }
  }

  @Test fun `inner interface in a merged component with different scope are merged`() {
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
        """
    ) {
      val innerInterface = classLoader
          .loadClass("com.squareup.test.SubcomponentInterface\$InnerInterface")
      assertThat(componentInterface extends innerInterface).isTrue()
      assertThat(componentInterface.interfaces).hasLength(1)
      assertThat(subcomponentInterface.interfaces).hasLength(0)
    }
  }

  @Test fun `module interfaces are not merged`() {
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
        """
    ) {
      assertThat(componentInterface extends contributingInterface).isTrue()
      assertThat(componentInterface extends secondContributingInterface).isFalse()
    }
  }
}
