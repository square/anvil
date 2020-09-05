package com.squareup.anvil.compiler

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.COMPILATION_ERROR
import org.junit.Test

class MergeModulesTest {

  @Test fun `Dagger modules are empty without arguments`() {
    compile(
        """
        package com.squareup.test
        
        import com.squareup.anvil.annotations.compat.MergeModules
        
        @MergeModules(Any::class)
        class DaggerModule1
        """
    ) {
      assertThat(daggerModule1.daggerModule.includes.withoutAnvilModule()).isEmpty()
      assertThat(daggerModule1.daggerModule.subcomponents).isEmpty()
    }
  }

  @Test fun `included modules are added in the composite module`() {
    compile(
        """
        package com.squareup.test

        import com.squareup.anvil.annotations.compat.MergeModules

        @MergeModules(
            scope = Any::class,
            includes = [
              Boolean::class,
              Int::class
            ]
        )
        class DaggerModule1
        """
    ) {
      assertThat(daggerModule1.daggerModule.includes.withoutAnvilModule())
          .containsExactly(Boolean::class, Int::class)
    }
  }

  @Test fun `includes and subcomponents are added in the Dagger module`() {
    compile(
        """
        package com.squareup.test

        import com.squareup.anvil.annotations.compat.MergeModules

        @MergeModules(
            scope = Any::class,
            includes = [
              Boolean::class,
              Int::class
            ],
            subcomponents = [
              Boolean::class,
              Int::class
            ]
        )
        class DaggerModule1
        """
    ) {
      val module = daggerModule1.daggerModule
      assertThat(module.includes.withoutAnvilModule()).containsExactly(Boolean::class, Int::class)
      assertThat(module.subcomponents.toList()).containsExactly(Boolean::class, Int::class)
    }
  }

  @Test fun `it's not allowed to have @Module and @MergeModules annotation at the same time`() {
    compile(
        """
        package com.squareup.test

        import com.squareup.anvil.annotations.compat.MergeModules

        @MergeModules(Any::class)
        @dagger.Module
        class DaggerModule1
        """
    ) {
      assertThat(exitCode).isEqualTo(COMPILATION_ERROR)
      // Position to the class.
      assertThat(messages).contains("Source.kt: (7, 7)")
    }
  }

  @Test fun `modules are merged`() {
    compile(
        """
        package com.squareup.test

        import com.squareup.anvil.annotations.compat.MergeModules
        import com.squareup.anvil.annotations.ContributesTo

        @ContributesTo(Any::class)
        @dagger.Module
        abstract class DaggerModule2

        @MergeModules(Any::class)
        class DaggerModule1
        """
    ) {
      assertThat(daggerModule1.daggerModule.includes.withoutAnvilModule())
          .containsExactly(daggerModule2.kotlin)
    }
  }

  @Test fun `modules are merged with included modules`() {
    compile(
        """
        package com.squareup.test

        import com.squareup.anvil.annotations.compat.MergeModules
        import com.squareup.anvil.annotations.ContributesTo

        @ContributesTo(Any::class)
        @dagger.Module
        abstract class DaggerModule2

        @MergeModules(
            scope = Any::class,
            includes = [
              Boolean::class,
              Int::class
            ]
        )
        class DaggerModule1
        """
    ) {
      assertThat(daggerModule1.daggerModule.includes.withoutAnvilModule())
          .containsExactly(daggerModule2.kotlin, Int::class, Boolean::class)
    }
  }

  @Test fun `contributing module must be a Dagger Module`() {
    compile(
        """
        package com.squareup.test

        import com.squareup.anvil.annotations.ContributesTo
        import com.squareup.anvil.annotations.compat.MergeModules

        @ContributesTo(Any::class)
        abstract class DaggerModule1

        @MergeModules(Any::class)
        class DaggerModule1
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

        import com.squareup.anvil.annotations.compat.MergeModules
        import com.squareup.anvil.annotations.ContributesTo

        @ContributesTo(Any::class)
        @dagger.Module
        abstract class DaggerModule2

        @ContributesTo(
            Any::class,
            replaces = [DaggerModule2::class]
        )
        @dagger.Module
        abstract class DaggerModule3

        @MergeModules(Any::class)
        class DaggerModule1
        """
    ) {
      assertThat(daggerModule1.daggerModule.includes.withoutAnvilModule())
          .containsExactly(daggerModule3.kotlin)
    }
  }

  @Test fun `contributed binding can be replaced`() {
    compile(
        """
        package com.squareup.test

        import com.squareup.anvil.annotations.compat.MergeModules
        import com.squareup.anvil.annotations.ContributesBinding
        import com.squareup.anvil.annotations.ContributesTo

        interface ParentInterface

        @ContributesBinding(Any::class)
        interface ContributingInterface : ParentInterface

        @ContributesTo(
            Any::class,
            replaces = [ContributingInterface::class]
        )
        @dagger.Module
        abstract class DaggerModule2

        @MergeModules(Any::class)
        class DaggerModule1
        """
    ) {
      assertThat(daggerModule1.daggerModule.includes.withoutAnvilModule())
          .containsExactly(daggerModule2.kotlin)

      assertThat(daggerModule1AnvilModule.declaredMethods).isEmpty()
    }
  }

  @Test fun `contributed binding can be replaced but must have the same scope`() {
    compile(
        """
        package com.squareup.test

        import com.squareup.anvil.annotations.compat.MergeModules
        import com.squareup.anvil.annotations.ContributesBinding
        import com.squareup.anvil.annotations.ContributesTo

        interface ParentInterface

        @ContributesBinding(Unit::class)
        interface ContributingInterface : ParentInterface

        @ContributesTo(
            Any::class,
            replaces = [ContributingInterface::class]
        )
        @dagger.Module
        abstract class DaggerModule2

        @MergeModules(Any::class)
        class DaggerModule1
        """
    ) {
      assertThat(exitCode).isEqualTo(COMPILATION_ERROR)
      // Position to the class.
      assertThat(messages).contains("Source.kt: (17, 16)")
      assertThat(messages).contains(
          "com.squareup.test.DaggerModule2 with scope kotlin.Any wants to replace " +
              "com.squareup.test.ContributingInterface with scope kotlin.Unit. The replacement " +
              "must use the same scope."
      )
    }
  }

  @Test fun `module can be replaced by contributed binding`() {
    compile(
        """
        package com.squareup.test

        import com.squareup.anvil.annotations.compat.MergeModules
        import com.squareup.anvil.annotations.ContributesBinding
        import com.squareup.anvil.annotations.ContributesTo

        interface ParentInterface

        @ContributesTo(Any::class)
        @dagger.Module
        abstract class DaggerModule2

        @ContributesBinding(
            Any::class,
            replaces = [DaggerModule2::class]
        )
        interface ContributingInterface : ParentInterface

        @MergeModules(Any::class)
        class DaggerModule1
        """
    ) {
      assertThat(daggerModule1.daggerModule.includes.withoutAnvilModule()).isEmpty()
      assertThat(daggerModule1AnvilModule.declaredMethods).hasLength(1)
    }
  }

  @Test fun `module replaced by contributed binding must use the same scope`() {
    compile(
        """
        package com.squareup.test

        import com.squareup.anvil.annotations.compat.MergeModules
        import com.squareup.anvil.annotations.ContributesBinding
        import com.squareup.anvil.annotations.ContributesTo

        interface ParentInterface

        @ContributesTo(Unit::class)
        @dagger.Module
        abstract class DaggerModule2

        @ContributesBinding(
            Any::class,
            replaces = [DaggerModule2::class]
        )
        interface ContributingInterface : ParentInterface

        @MergeModules(Any::class)
        class DaggerModule1
        """
    ) {
      assertThat(exitCode).isEqualTo(COMPILATION_ERROR)
      // Position to the class.
      assertThat(messages).contains("Source.kt: (17, 11)")
      assertThat(messages).contains(
          "com.squareup.test.ContributingInterface with scope kotlin.Any wants to replace " +
              "com.squareup.test.DaggerModule2 with scope kotlin.Unit. The replacement must use " +
              "the same scope."
      )
    }
  }

  @Test fun `replaced modules must be Dagger modules`() {
    compile(
        """
        package com.squareup.test

        import com.squareup.anvil.annotations.compat.MergeModules
        import com.squareup.anvil.annotations.ContributesTo

        abstract class DaggerModule3

        @ContributesTo(
            Any::class,
            replaces = [DaggerModule3::class]
        )
        @dagger.Module
        abstract class DaggerModule2

        @MergeModules(Any::class)
        class DaggerModule1
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

        import com.squareup.anvil.annotations.compat.MergeModules
        import com.squareup.anvil.annotations.ContributesTo

        @dagger.Module
        @ContributesTo(Unit::class)
        abstract class DaggerModule3

        @ContributesTo(
            Any::class,
            replaces = [DaggerModule3::class]
        )
        @dagger.Module
        abstract class DaggerModule2

        @MergeModules(Any::class)
        class DaggerModule1
        """
    ) {
      assertThat(exitCode).isEqualTo(COMPILATION_ERROR)
      // Position to the class.
      assertThat(messages).contains("Source.kt: (15, 16)")
      assertThat(messages).contains(
          "com.squareup.test.DaggerModule2 with scope kotlin.Any wants to replace " +
              "com.squareup.test.DaggerModule3 with scope kotlin.Unit. The replacement must use " +
              "the same scope."
      )
    }
  }

  @Test fun `included modules are not replaced`() {
    compile(
        """
        package com.squareup.test

        import com.squareup.anvil.annotations.compat.MergeModules
        import com.squareup.anvil.annotations.ContributesTo

        @dagger.Module
        @ContributesTo(Any::class)
        abstract class DaggerModule2

        @ContributesTo(
            Any::class,
            replaces = [DaggerModule2::class]
        )
        @dagger.Module
        abstract class DaggerModule3

        @MergeModules(
            scope = Any::class,
            includes = [
              DaggerModule2::class
            ]
        )
        class DaggerModule1
        """
    ) {
      assertThat(daggerModule1.daggerModule.includes.withoutAnvilModule())
          .containsExactly(daggerModule2.kotlin, daggerModule3.kotlin)
    }
  }

  @Test fun `modules can be excluded`() {
    compile(
        """
        package com.squareup.test

        import com.squareup.anvil.annotations.compat.MergeModules
        import com.squareup.anvil.annotations.ContributesTo

        @ContributesTo(Any::class)
        @dagger.Module
        abstract class DaggerModule2

        @ContributesTo(Any::class)
        @dagger.Module
        abstract class DaggerModule3

        @MergeModules(
            scope = Any::class,
            exclude = [
              DaggerModule2::class
            ]
        )
        class DaggerModule1
        """
    ) {
      assertThat(daggerModule1.daggerModule.includes.withoutAnvilModule())
          .containsExactly(daggerModule3.kotlin)
    }
  }

  @Test fun `excluded modules must use the same scope`() {
    compile(
        """
        package com.squareup.test

        import com.squareup.anvil.annotations.compat.MergeModules
        import com.squareup.anvil.annotations.ContributesTo

        @ContributesTo(Unit::class)
        @dagger.Module
        abstract class DaggerModule2

        @MergeModules(
            scope = Any::class,
            exclude = [
              DaggerModule2::class
            ]
        )
        class DaggerModule1
        """
    ) {
      assertThat(exitCode).isEqualTo(COMPILATION_ERROR)
      // Position to the class.
      assertThat(messages).contains("Source.kt: (16, 7)")
      assertThat(messages).contains(
          "com.squareup.test.DaggerModule1 with scope kotlin.Any wants to exclude " +
              "com.squareup.test.DaggerModule2 with scope kotlin.Unit. The exclusion must " +
              "use the same scope."
      )
    }
  }

  @Test fun `contributed bindings can be excluded`() {
    compile(
        """
        package com.squareup.test

        import com.squareup.anvil.annotations.compat.MergeModules
        import com.squareup.anvil.annotations.ContributesBinding

        interface ParentInterface

        @ContributesBinding(Any::class)
        interface ContributingInterface : ParentInterface

        @MergeModules(
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

        import com.squareup.anvil.annotations.compat.MergeModules
        import com.squareup.anvil.annotations.ContributesBinding

        interface ParentInterface

        @ContributesBinding(Unit::class)
        interface ContributingInterface : ParentInterface

        @MergeModules(
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

  @Test fun `modules are added to merged modules with corresponding scope`() {
    compile(
        """
        package com.squareup.test

        import com.squareup.anvil.annotations.compat.MergeModules
        import com.squareup.anvil.annotations.ContributesTo

        @ContributesTo(Any::class)
        @dagger.Module
        abstract class DaggerModule3

        @ContributesTo(Unit::class)
        @dagger.Module
        abstract class DaggerModule4

        @MergeModules(Any::class)
        class DaggerModule1

        @MergeModules(Unit::class)
        class DaggerModule2
        """
    ) {
      assertThat(daggerModule1.daggerModule.includes.withoutAnvilModule())
          .containsExactly(daggerModule3.kotlin)
      assertThat(daggerModule2.daggerModule.includes.withoutAnvilModule())
          .containsExactly(daggerModule4.kotlin)
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
  
          import com.squareup.anvil.annotations.compat.MergeModules
          import com.squareup.anvil.annotations.ContributesTo
  
          @ContributesTo(Any::class)
          @dagger.Module
          $visibility abstract class DaggerModule2
  
          @MergeModules(Any::class)
          class DaggerModule1
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

        import com.squareup.anvil.annotations.compat.MergeModules
        import com.squareup.anvil.annotations.ContributesTo

        @MergeModules(Any::class)
        class DaggerModule1 {
          @ContributesTo(Any::class)
          @dagger.Module
          abstract class InnerModule
        }
        """
    ) {
      val innerModule = classLoader.loadClass("com.squareup.test.DaggerModule1\$InnerModule")

      assertThat(daggerModule1.daggerModule.includes.withoutAnvilModule())
          .containsExactly(innerModule.kotlin)
    }
  }

  @Test fun `a module is not allowed to be included and excluded`() {
    compile(
        """
        package com.squareup.test

        import com.squareup.anvil.annotations.compat.MergeModules
        import com.squareup.anvil.annotations.ContributesTo

        @ContributesTo(Any::class)
        @dagger.Module
        abstract class DaggerModule1

        @MergeModules(
            scope = Any::class,
            includes = [
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
}
