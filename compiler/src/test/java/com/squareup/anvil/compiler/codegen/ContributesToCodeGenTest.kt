package com.squareup.anvil.compiler.codegen

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.compiler.componentInterface
import com.squareup.anvil.compiler.contributingInterface
import com.squareup.anvil.compiler.daggerModule1
import com.squareup.anvil.compiler.daggerModule2
import com.squareup.anvil.compiler.hintContributes
import com.squareup.anvil.compiler.hintContributesScope
import com.squareup.anvil.compiler.hintContributesScopes
import com.squareup.anvil.compiler.innerInterface
import com.squareup.anvil.compiler.innerModule
import com.squareup.anvil.compiler.internal.testing.AnvilCompilationMode
import com.squareup.anvil.compiler.testing.AnvilCompilationModeTest
import com.squareup.anvil.compiler.walkGeneratedFiles
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode
import org.junit.jupiter.api.TestFactory

class ContributesToCodeGenTest : AnvilCompilationModeTest(
  AnvilCompilationMode.Embedded(),
  AnvilCompilationMode.Ksp(),
) {

  @TestFactory
  fun `there is no hint for merge annotations`() = testFactory {
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.MergeComponent
      
      @MergeComponent(Any::class)
      interface ComponentInterface
      """,
    ) {
      assertThat(componentInterface.hintContributes).isNull()
      assertThat(componentInterface.hintContributesScope).isNull()
    }

    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.MergeSubcomponent
      
      @MergeSubcomponent(Any::class)
      interface ComponentInterface
      """,
    ) {
      assertThat(componentInterface.hintContributes).isNull()
      assertThat(componentInterface.hintContributesScope).isNull()
    }
  }

  @TestFactory
  fun `there is a hint for contributed Dagger modules`() = testFactory {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesTo

      @ContributesTo(Any::class)
      @dagger.Module
      abstract class DaggerModule1
      """,
    ) {
      assertThat(daggerModule1.hintContributes?.java).isEqualTo(daggerModule1)
      assertThat(daggerModule1.hintContributesScope).isEqualTo(Any::class)

      val generatedFile = walkGeneratedFiles(mode).single()

      assertThat(generatedFile.name).isEqualTo("com_squareup_test_DaggerModule1_52b5c4c4.kt")
    }
  }

  @TestFactory
  fun `there are multiple hints for contributed Dagger modules`() = testFactory {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesTo
      import dagger.Module

      @ContributesTo(Any::class)
      @ContributesTo(Unit::class)
      @Module
      abstract class DaggerModule1
      """,
    ) {
      assertThat(daggerModule1.hintContributes?.java).isEqualTo(daggerModule1)
      assertThat(daggerModule1.hintContributesScopes).containsExactly(Any::class, Unit::class)
    }
  }

  @TestFactory
  fun `the scopes for multiple contributions have a stable sort`() = testFactory {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesTo
      import dagger.Module

      @ContributesTo(Any::class)
      @ContributesTo(Unit::class)
      @Module
      abstract class DaggerModule1

      @ContributesTo(Unit::class)
      @ContributesTo(Any::class)
      @Module
      abstract class DaggerModule2
      """,
    ) {
      assertThat(daggerModule1.hintContributesScopes)
        .containsExactly(Any::class, Unit::class)
        .inOrder()
      assertThat(daggerModule2.hintContributesScopes)
        .containsExactly(Any::class, Unit::class)
        .inOrder()
    }
  }

  @TestFactory
  fun `there are multiple hints for contributed Dagger modules with fully qualified names`() =
    testFactory {
      compile(
        """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesTo

      @ContributesTo(Any::class)
      @com.squareup.anvil.annotations.ContributesTo(Unit::class)
      @dagger.Module
      abstract class DaggerModule1
      """,
      ) {
        assertThat(daggerModule1.hintContributes?.java).isEqualTo(daggerModule1)
        assertThat(daggerModule1.hintContributesScopes).containsExactly(Any::class, Unit::class)
      }
    }

  @TestFactory
  fun `there are multiple hints for contributed Dagger modules with star imports`() = testFactory {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.*

      @ContributesTo(Any::class)
      @com.squareup.anvil.annotations.ContributesTo(Unit::class)
      @dagger.Module
      abstract class DaggerModule1
      """,
    ) {
      assertThat(daggerModule1.hintContributes?.java).isEqualTo(daggerModule1)
      assertThat(daggerModule1.hintContributesScopes).containsExactly(Any::class, Unit::class)
    }
  }

  @TestFactory
  fun `multiple annotations with the same scope aren't allowed`() = testFactory {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesTo

      @ContributesTo(Any::class)
      @ContributesTo(Any::class, replaces = [Int::class])
      @ContributesTo(Unit::class)
      @ContributesTo(Unit::class, replaces = [Int::class])
      @dagger.Module
      abstract class DaggerModule1
      """,
      expectExitCode = ExitCode.COMPILATION_ERROR,
    ) {
      assertThat(messages).contains(
        "com.squareup.test.DaggerModule1 contributes multiple times to the same scope: " +
          "[Any, Unit]. Contributing multiple times to the same scope is forbidden and all " +
          "scopes must be distinct.",
      )
    }
  }

  @TestFactory
  fun `a file can contain an internal class`() = testFactory {
    // There was a bug that triggered a failure. Make sure that it doesn't happen again.
    // https://github.com/square/anvil/issues/232
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesTo

      @ContributesTo(Any::class)
      @dagger.Module
      abstract class DaggerModule1
      
      @PublishedApi
      internal class FailAnvil
      """,
    ) {
      assertThat(daggerModule1.hintContributes?.java).isEqualTo(daggerModule1)
      assertThat(daggerModule1.hintContributesScope).isEqualTo(Any::class)
    }
  }

  @TestFactory
  fun `there is a hint for contributed interfaces`() = testFactory {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesTo

      @ContributesTo(Any::class)
      interface ContributingInterface
      """,
    ) {
      assertThat(contributingInterface.hintContributes?.java).isEqualTo(contributingInterface)
      assertThat(contributingInterface.hintContributesScope).isEqualTo(Any::class)
    }
  }

  @TestFactory
  fun `the order of the scope can be changed with named parameters`() = testFactory {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesTo

      @ContributesTo(replaces = [Unit::class], scope = Int::class)
      interface ContributingInterface
      """,
    ) {
      assertThat(contributingInterface.hintContributesScope).isEqualTo(Int::class)
    }
  }

  @TestFactory
  fun `there is a hint for contributed inner Dagger modules`() = testFactory {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesTo

      interface ComponentInterface {
        @ContributesTo(Any::class)
        @dagger.Module
        abstract class InnerModule
      }
      """,
    ) {
      assertThat(innerModule.hintContributes?.java).isEqualTo(innerModule)
      assertThat(innerModule.hintContributesScope).isEqualTo(Any::class)

      val generatedFile = walkGeneratedFiles(mode).single()

      assertThat(generatedFile.name)
        .isEqualTo("com_squareup_test_ComponentInterface_InnerModule_29ca774b.kt")
    }
  }

  @TestFactory
  fun `there is a hint for contributed inner interfaces`() = testFactory {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesTo

      class SomeClass {
        @ContributesTo(Any::class)
        interface InnerInterface
      }
      """,
    ) {
      assertThat(innerInterface.hintContributes?.java).isEqualTo(innerInterface)
      assertThat(innerInterface.hintContributesScope).isEqualTo(Any::class)
    }
  }

  @TestFactory
  fun `contributing module must be a Dagger Module`() = testFactory {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesTo

      @ContributesTo(Any::class)
      abstract class DaggerModule1
      """,
      expectExitCode = ExitCode.COMPILATION_ERROR,
    ) {
      // Position to the class.
      assertThat(messages).contains("Source0.kt:6")
      assertThat(messages).contains(
        "com.squareup.test.DaggerModule1 is annotated with @ContributesTo, but this class " +
          "is neither an interface nor a Dagger module. Did you forget to add @Module?",
      )
    }
  }

  @TestFactory
  fun `contributed modules must be public`() = testFactory {
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

        @ContributesTo(Any::class)
        @dagger.Module
        $visibility abstract class DaggerModule1
        """,
        expectExitCode = ExitCode.COMPILATION_ERROR,
      ) {
        // Position to the class.
        assertThat(messages).contains("Source0.kt:7")
        assertThat(messages).contains(
          "com.squareup.test.DaggerModule1 is contributed to the Dagger graph, but the " +
            "module is not public. Only public modules are supported.",
        )
      }
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

        @ContributesTo(Any::class)
        $visibility interface ContributingInterface
        """,
        expectExitCode = ExitCode.COMPILATION_ERROR,
      ) {
        // Position to the class.
        assertThat(messages).contains("Source0.kt:6")
        assertThat(messages).contains(
          "com.squareup.test.ContributingInterface is contributed to the Dagger graph, but the " +
            "module is not public. Only public modules are supported.",
        )
      }
    }
  }
}
