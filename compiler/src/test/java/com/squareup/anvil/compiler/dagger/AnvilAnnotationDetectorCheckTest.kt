package com.squareup.anvil.compiler.dagger

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.compiler.internal.testing.AnvilCompilationMode
import com.squareup.anvil.compiler.internal.testing.compileAnvil
import com.squareup.anvil.compiler.isError
import com.squareup.anvil.compiler.testing.AnvilCompilationModeTest
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.OK
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.TestFactory

class AnvilAnnotationDetectorCheckTest : AnvilCompilationModeTest(
  AnvilCompilationMode.Embedded(),
  AnvilCompilationMode.Ksp(),
) {

  @TestFactory fun `a Dagger subcomponent is allowed`() = testFactory {
    compile(
      """
      package com.squareup.test
      
      @dagger.Subcomponent
      interface ComponentInterface
      """,
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }

  @TestFactory fun `@ContributesTo is not allowed`() = testFactory {
    compile(
      """
      package com.squareup.test
      
      import dagger.Subcomponent
      
      @com.squareup.anvil.annotations.ContributesTo(Any::class)
      class AnyClass
      """,
    ) {
      assertError()
    }
  }

  @TestFactory fun `@ContributesBinding is not allowed`() = testFactory {
    compile(
      """
      package com.squareup.test
      
      import dagger.Subcomponent
      
      @com.squareup.anvil.annotations.ContributesBinding(Any::class)
      class AnyClass
      """,
    ) {
      assertError()
    }
  }

  @TestFactory fun `@ContributeSubcomponent is not allowed`() = testFactory {
    compile(
      """
      package com.squareup.test
      
      import dagger.Subcomponent
      
      @com.squareup.anvil.annotations.ContributesSubcomponent(Any::class, Unit::class)
      class AnyClass
      """,
    ) {
      assertError()
    }
  }

  @TestFactory fun `@MergeComponent is not allowed`() = testFactory {
    compile(
      """
      package com.squareup.test
      
      import dagger.Subcomponent
      
      @com.squareup.anvil.annotations.MergeComponent(Any::class)
      class AnyClass
      """,
    ) {
      assertError()
    }
  }

  @TestFactory fun `@MergeSubcomponent is not allowed`() = testFactory {
    compile(
      """
      package com.squareup.test
      
      import dagger.Subcomponent
      
      @com.squareup.anvil.annotations.MergeSubcomponent(Any::class)
      class AnyClass
      """,
    ) {
      assertError()
    }
  }

  @TestFactory fun `@MergeModules is not allowed`() = testFactory {
    compile(
      """
      package com.squareup.test
      
      import dagger.Subcomponent
      
      @com.squareup.anvil.annotations.compat.MergeModules(Any::class)
      class AnyClass
      """,
    ) {
      assertError()
    }
  }

  @TestFactory fun `@MergeInterfaces is not allowed`() = testFactory {
    compile(
      """
      package com.squareup.test
      
      import dagger.Subcomponent
      
      @com.squareup.anvil.annotations.compat.MergeInterfaces(Any::class)
      class AnyClass
      """,
    ) {
      assertError()
    }
  }

  private fun JvmCompilationResult.assertError() {
    assertThat(exitCode).isError()
    assertThat(messages).contains("Source0.kt:6:7")
    assertThat(messages).contains(
      "This Gradle module is configured to ONLY generate Dagger factories with the " +
        "`generateDaggerFactoriesOnly` flag. However, this module contains code that uses " +
        "other Anvil annotations. That's not supported.",
    )
  }

  private fun compile(
    @Language("kotlin") vararg sources: String,
    block: JvmCompilationResult.() -> Unit = { },
  ): JvmCompilationResult = compileAnvil(
    sources = sources,
    generateDaggerFactories = true,
    generateDaggerFactoriesOnly = true,
    block = block,
  )
}
