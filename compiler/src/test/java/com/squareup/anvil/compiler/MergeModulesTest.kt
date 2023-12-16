package com.squareup.anvil.compiler

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.annotations.compat.MergeModules
import com.squareup.anvil.compiler.internal.testing.daggerModule
import com.squareup.anvil.compiler.internal.testing.withoutAnvilModules
import org.junit.jupiter.api.Test

class MergeModulesTest : DefaultTestEnvironmentTest {

  @Test fun `Dagger modules are empty without arguments`() = test {
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.compat.MergeModules
      
      @MergeModules(Any::class)
      class DaggerModule1
      """,
    ) {
      assertThat(daggerModule1.daggerModule.includes.withoutAnvilModules()).isEmpty()
      assertThat(daggerModule1.daggerModule.subcomponents).isEmpty()
    }
  }

  @Test fun `included modules are added in the composite module`() = test {
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
      """,
    ) {
      assertThat(daggerModule1.daggerModule.includes.withoutAnvilModules())
        .containsExactly(Boolean::class, Int::class)
    }
  }

  @Test fun `includes and subcomponents are added in the Dagger module`() = test {
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
      """,
    ) {
      val module = daggerModule1.daggerModule
      assertThat(module.includes.withoutAnvilModules()).containsExactly(Boolean::class, Int::class)
      assertThat(module.subcomponents.toList()).containsExactly(Boolean::class, Int::class)
    }
  }

  @Test fun `it's not allowed to have @Module and @MergeModules annotation at the same time`() = test {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.compat.MergeModules

      @MergeModules(Any::class)
      @dagger.Module
      class DaggerModule1
      """,
    ) {
      assertThat(exitCode).isError()
      // Position to the class.
      assertThat(messages).contains("Source0.kt:7:7")
    }
  }

  @Test fun `modules are merged`() = test {
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
      """,
    ) {
      assertThat(daggerModule1.daggerModule.includes.withoutAnvilModules())
        .containsExactly(daggerModule2.kotlin)
    }
  }

  @Test fun `modules are merged with included modules`() = test {
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
      """,
    ) {
      assertThat(daggerModule1.daggerModule.includes.withoutAnvilModules())
        .containsExactly(daggerModule2.kotlin, Int::class, Boolean::class)
    }
  }

  @Test fun `contributing module must be a Dagger Module`() = test {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesTo
      import com.squareup.anvil.annotations.compat.MergeModules

      @ContributesTo(Any::class)
      abstract class DaggerModule2

      @MergeModules(Any::class)
      class DaggerModule1
      """,
    ) {
      assertThat(exitCode).isError()
      // Position to the class.
      assertThat(messages).contains("Source0.kt:7:16")
    }
  }

  @Test fun `module can be replaced`() = test {
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
      """,
    ) {
      assertThat(daggerModule1.daggerModule.includes.withoutAnvilModules())
        .containsExactly(daggerModule3.kotlin)
    }
  }

  @Test fun `contributed binding can be replaced`() = test {
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
      """,
    ) {
      assertThat(daggerModule1.daggerModule.includes.withoutAnvilModules())
        .containsExactly(daggerModule2.kotlin)

      assertThat(
        daggerModule1.mergedModules(MergeModules::class).flatMapArray {
          it.java.declaredMethods
        },
      ).isEmpty()
    }
  }

  @Test fun `contributed multiinding can be replaced`() = test {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.compat.MergeModules
      import com.squareup.anvil.annotations.ContributesMultibinding
      import com.squareup.anvil.annotations.ContributesTo

      interface ParentInterface

      @ContributesMultibinding(Any::class)
      interface ContributingInterface : ParentInterface

      @ContributesTo(
          Any::class,
          replaces = [ContributingInterface::class]
      )
      @dagger.Module
      abstract class DaggerModule2

      @MergeModules(Any::class)
      class DaggerModule1
      """,
    ) {
      assertThat(daggerModule1.daggerModule.includes.withoutAnvilModules())
        .containsExactly(daggerModule2.kotlin)

      assertThat(
        daggerModule1.mergedModules(MergeModules::class).flatMapArray {
          it.java.declaredMethods
        },
      ).isEmpty()
    }
  }

  @Test fun `contributed binding can be replaced but must have the same scope`() = test {
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
      """,
    ) {
      assertThat(exitCode).isError()
      // Position to the class.
      assertThat(messages).contains("Source0.kt:17:16")
      assertThat(messages).contains(
        "com.squareup.test.DaggerModule2 with scopes [kotlin.Any] wants to replace " +
          "com.squareup.test.ContributingInterface, but the replaced class isn't contributed " +
          "to the same scope.",
      )
    }
  }

  @Test fun `contributed multibinding can be replaced but must have the same scope`() = test {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.compat.MergeModules
      import com.squareup.anvil.annotations.ContributesMultibinding
      import com.squareup.anvil.annotations.ContributesTo

      interface ParentInterface

      @ContributesMultibinding(Unit::class)
      interface ContributingInterface : ParentInterface

      @ContributesTo(
          Any::class,
          replaces = [ContributingInterface::class]
      )
      @dagger.Module
      abstract class DaggerModule2

      @MergeModules(Any::class)
      class DaggerModule1
      """,
    ) {
      assertThat(exitCode).isError()
      // Position to the class.
      assertThat(messages).contains("Source0.kt:17:16")
      assertThat(messages).contains(
        "com.squareup.test.DaggerModule2 with scopes [kotlin.Any] wants to replace " +
          "com.squareup.test.ContributingInterface, but the replaced class isn't contributed " +
          "to the same scope.",
      )
    }
  }

  @Test fun `module can be replaced by contributed binding`() = test {
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
      """,
    ) {
      assertThat(daggerModule1.daggerModule.includes.withoutAnvilModules()).isEmpty()
      assertThat(
        daggerModule1.mergedModules(MergeModules::class).flatMapArray {
          it.java.declaredMethods
        },
      ).hasSize(1)
    }
  }

  @Test fun `module can be replaced by contributed multibinding`() = test {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.compat.MergeModules
      import com.squareup.anvil.annotations.ContributesMultibinding
      import com.squareup.anvil.annotations.ContributesTo

      interface ParentInterface

      @ContributesTo(Any::class)
      @dagger.Module
      abstract class DaggerModule2

      @ContributesMultibinding(
          Any::class,
          replaces = [DaggerModule2::class]
      )
      interface ContributingInterface : ParentInterface

      @MergeModules(Any::class)
      class DaggerModule1
      """,
    ) {
      assertThat(daggerModule1.daggerModule.includes.withoutAnvilModules()).isEmpty()
      assertThat(
        daggerModule1.mergedModules(MergeModules::class).flatMapArray {
          it.java.declaredMethods
        },
      ).hasSize(1)
    }
  }

  @Test fun `module replaced by contributed binding must use the same scope`() = test {
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
      """,
    ) {
      assertThat(exitCode).isError()
      // Position to the class.
      assertThat(messages).contains("Source0.kt:17:11")
      assertThat(messages).contains(
        "com.squareup.test.ContributingInterface with scopes [kotlin.Any] wants to replace " +
          "com.squareup.test.DaggerModule2, but the replaced class isn't contributed " +
          "to the same scope.",
      )
    }
  }

  @Test fun `module replaced by contributed multibinding must use the same scope`() = test {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.compat.MergeModules
      import com.squareup.anvil.annotations.ContributesMultibinding
      import com.squareup.anvil.annotations.ContributesTo

      interface ParentInterface

      @ContributesTo(Unit::class)
      @dagger.Module
      abstract class DaggerModule2

      @ContributesMultibinding(
          Any::class,
          replaces = [DaggerModule2::class]
      )
      interface ContributingInterface : ParentInterface

      @MergeModules(Any::class)
      class DaggerModule1
      """,
    ) {
      assertThat(exitCode).isError()
      // Position to the class.
      assertThat(messages).contains("Source0.kt:17:11")
      assertThat(messages).contains(
        "com.squareup.test.ContributingInterface with scopes [kotlin.Any] wants to replace " +
          "com.squareup.test.DaggerModule2, but the replaced class isn't contributed " +
          "to the same scope.",
      )
    }
  }

  @Test fun `replaced modules must be Dagger modules`() = test {
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
      """,
    ) {
      assertThat(exitCode).isError()
      // Position to the class.
      assertThat(messages).contains("Source0.kt:13:16")
    }
  }

  @Test fun `replaced modules must use the same scope`() = test {
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
      """,
    ) {
      assertThat(exitCode).isError()
      // Position to the class.
      assertThat(messages).contains("Source0.kt:15:16")
      assertThat(messages).contains(
        "com.squareup.test.DaggerModule2 with scopes [kotlin.Any] wants to replace " +
          "com.squareup.test.DaggerModule3, but the replaced class isn't contributed " +
          "to the same scope.",
      )
    }
  }

  @Test fun `included modules are not replaced`() = test {
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
      """,
    ) {
      assertThat(daggerModule1.daggerModule.includes.withoutAnvilModules())
        .containsExactly(daggerModule2.kotlin, daggerModule3.kotlin)
    }
  }

  @Test fun `modules can be excluded`() = test {
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
      """,
    ) {
      assertThat(daggerModule1.daggerModule.includes.withoutAnvilModules())
        .containsExactly(daggerModule3.kotlin)
    }
  }

  @Test fun `excluded modules must use the same scope`() = test {
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
      """,
    ) {
      assertThat(exitCode).isError()
      // Position to the class.
      assertThat(messages).contains("Source0.kt:16:7")
      assertThat(messages).contains(
        "com.squareup.test.DaggerModule1 with scopes [kotlin.Any] wants to exclude " +
          "com.squareup.test.DaggerModule2, but the excluded class isn't contributed " +
          "to the same scope.",
      )
    }
  }

  @Test fun `contributed bindings can be excluded`() = test {
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
      """,
    ) {
      assertThat(
        componentInterface.mergedModules(MergeModules::class).flatMapArray {
          it.java.declaredMethods
        },
      ).isEmpty()
    }
  }

  @Test fun `contributed multibindings can be excluded`() = test {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.compat.MergeModules
      import com.squareup.anvil.annotations.ContributesMultibinding

      interface ParentInterface

      @ContributesMultibinding(Any::class)
      interface ContributingInterface : ParentInterface

      @MergeModules(
          scope = Any::class,
          exclude = [
            ContributingInterface::class
          ]
      )
      interface ComponentInterface
      """,
    ) {
      assertThat(
        componentInterface.mergedModules(MergeModules::class).flatMapArray {
          it.java.declaredMethods
        },
      ).isEmpty()
    }
  }

  @Test fun `contributed bindings can be excluded but must use the same scope`() = test {
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
      """,
    ) {
      assertThat(exitCode).isError()
      // Position to the class.
      assertThat(messages).contains("Source0.kt:17:11")
      assertThat(messages).contains(
        "com.squareup.test.ComponentInterface with scopes [kotlin.Any] wants to exclude " +
          "com.squareup.test.ContributingInterface, but the excluded class isn't contributed " +
          "to the same scope.",
      )
    }
  }

  @Test fun `contributed multibindings can be excluded but must use the same scope`() = test {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.compat.MergeModules
      import com.squareup.anvil.annotations.ContributesMultibinding

      interface ParentInterface

      @ContributesMultibinding(Unit::class)
      interface ContributingInterface : ParentInterface

      @MergeModules(
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
      assertThat(messages).contains("Source0.kt:17:11")
      assertThat(messages).contains(
        "com.squareup.test.ComponentInterface with scopes [kotlin.Any] wants to exclude " +
          "com.squareup.test.ContributingInterface, but the excluded class isn't contributed " +
          "to the same scope.",
      )
    }
  }

  @Test fun `modules are added to merged modules with corresponding scope`() = test {
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
      """,
    ) {
      assertThat(daggerModule1.daggerModule.includes.withoutAnvilModules())
        .containsExactly(daggerModule3.kotlin)
      assertThat(daggerModule2.daggerModule.includes.withoutAnvilModules())
        .containsExactly(daggerModule4.kotlin)
    }
  }

  @Test fun `contributed modules must be public`() = test {
    val visibilities = setOf(
      "internal",
      "private",
      "protected",
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
        """,
      ) {
        assertThat(exitCode).isError()
        // Position to the class.
        assertThat(messages).contains("Source0.kt:8:")
      }
    }
  }

  @Test fun `inner modules are merged`() = test {
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
      """,
    ) {
      val innerModule = classLoader.loadClass("com.squareup.test.DaggerModule1\$InnerModule")

      assertThat(daggerModule1.daggerModule.includes.withoutAnvilModules())
        .containsExactly(innerModule.kotlin)
    }
  }

  @Test fun `modules are merged without a package`() = test {
    compile(
      """
      import com.squareup.anvil.annotations.compat.MergeModules
      import com.squareup.anvil.annotations.ContributesTo

      @ContributesTo(Any::class)
      @dagger.Module
      abstract class DaggerModule2

      @MergeModules(Any::class)
      class DaggerModule1
      """,
    ) {
      assertThat(classLoader.loadClass("DaggerModule1").daggerModule.includes.withoutAnvilModules())
        .containsExactly(classLoader.loadClass("DaggerModule2").kotlin)
    }
  }

  @Test fun `inner contributed modules are merged`() = test {
    // This code snippet used to trigger an error, see https://github.com/square/anvil/issues/256
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.compat.MergeModules
      import com.squareup.anvil.annotations.ContributesBinding

      interface BaseFactory

      class Outer {
        @ContributesBinding(Any::class)
        object Factory : BaseFactory
      }
      
      @MergeModules(Any::class)
      class DaggerModule1
      """,
    ) {
      assertThat(
        daggerModule1.mergedModules(MergeModules::class).flatMapArray {
          it.java.declaredMethods
        },
      ).hasSize(1)
    }
  }

  @Test fun `a module is not allowed to be included and excluded`() = test {
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
      """,
    ) {
      assertThat(exitCode).isError()
      assertThat(messages).contains("Source0.kt:19:11")
    }
  }

  @Test fun `merged modules can be contributed to another scope at the same time`() = test {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.compat.MergeModules
      import com.squareup.anvil.annotations.ContributesTo
      import dagger.Module

      @ContributesTo(Any::class)
      @Module
      abstract class DaggerModule2

      @MergeModules(Any::class)
      @ContributesTo(Unit::class)
      class DaggerModule1

      @MergeModules(Unit::class)
      class DaggerModule3
      """,
    ) {
      assertThat(daggerModule1.daggerModule.includes.withoutAnvilModules())
        .containsExactly(daggerModule2.kotlin)

      assertThat(daggerModule3.daggerModule.includes.withoutAnvilModules())
        .containsExactly(daggerModule1.kotlin)
    }
  }

  @Test fun `modules contributed to multiple scopes are merged`() = test {
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.compat.MergeModules
      import com.squareup.anvil.annotations.ContributesTo
      
      @ContributesTo(Any::class)
      @ContributesTo(Unit::class)
      @dagger.Module
      abstract class DaggerModule3
      
      @ContributesTo(Any::class)
      @dagger.Module
      abstract class DaggerModule4
      
      @MergeModules(Any::class)
      class DaggerModule1
      
      @MergeModules(Unit::class)
      class DaggerModule2
      """,
    ) {
      assertThat(daggerModule1.daggerModule.includes.withoutAnvilModules())
        .containsExactly(daggerModule3.kotlin, daggerModule4.kotlin)

      assertThat(daggerModule2.daggerModule.includes.withoutAnvilModules())
        .containsExactly(daggerModule3.kotlin)
    }
  }

  @Test fun `modules contributed to multiple scopes can be replaced`() = test {
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.compat.MergeModules
      import com.squareup.anvil.annotations.ContributesTo
      
      @ContributesTo(Any::class)
      @ContributesTo(Unit::class)
      @dagger.Module
      abstract class DaggerModule3
      
      @ContributesTo(Any::class, replaces = [DaggerModule3::class])
      @dagger.Module
      abstract class DaggerModule4
      
      @MergeModules(Any::class)
      class DaggerModule1
      
      @MergeModules(Unit::class)
      class DaggerModule2
      """,
    ) {
      assertThat(daggerModule1.daggerModule.includes.withoutAnvilModules())
        .containsExactly(daggerModule4.kotlin)

      assertThat(daggerModule2.daggerModule.includes.withoutAnvilModules())
        .containsExactly(daggerModule3.kotlin)
    }
  }

  @Test fun `modules contributed to multiple scopes can be excluded in one scope`() = test {
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.compat.MergeModules
      import com.squareup.anvil.annotations.ContributesTo
      
      @ContributesTo(Any::class)
      @ContributesTo(Unit::class)
      @dagger.Module
      abstract class DaggerModule3
      
      @ContributesTo(Any::class)
      @dagger.Module
      abstract class DaggerModule4
      
      @MergeModules(Any::class, exclude = [DaggerModule3::class])
      class DaggerModule1
      
      @MergeModules(Unit::class)
      class DaggerModule2
      """,
    ) {
      assertThat(daggerModule1.daggerModule.includes.withoutAnvilModules())
        .containsExactly(daggerModule4.kotlin)

      assertThat(daggerModule2.daggerModule.includes.withoutAnvilModules())
        .containsExactly(daggerModule3.kotlin)
    }
  }
}
