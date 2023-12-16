package com.squareup.anvil.compiler.dagger

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.compiler.AnvilCompilationModeTest
import com.squareup.anvil.compiler.internal.testing.AnvilCompilationMode
import com.squareup.anvil.compiler.isError
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.OK
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD

@Execution(SAME_THREAD, reason = "KSP can't handle it")
class AnvilMergeAnnotationDetectorCheckTest : AnvilCompilationModeTest(
  AnvilCompilationMode.Embedded(),
  AnvilCompilationMode.Ksp(),
) {

  @TestFactory
  fun `@ContributesTo is allowed`() = testFactory {
    compile(
      """
      package com.squareup.test
      
      import dagger.Module
      
      @Module
      @com.squareup.anvil.annotations.ContributesTo(Any::class)
      object AnyClass
      """,
      disableComponentMerging = true,
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }

  @TestFactory
  fun `@ContributesBinding is allowed`() = testFactory {
    compile(
      """
      package com.squareup.test
      
      interface BaseType
      
      @com.squareup.anvil.annotations.ContributesBinding(Any::class)
      class AnyClass : BaseType
      """,
      disableComponentMerging = true,
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }

  @TestFactory
  fun `@MergeComponent is not allowed`() = testFactory {
    compile(
      """
      package com.squareup.test
      
      import dagger.Subcomponent
      
      @com.squareup.anvil.annotations.MergeComponent(Any::class)
      class AnyClass
      """,
      disableComponentMerging = true,
    ) {
      assertError()
    }
  }

  @TestFactory
  fun `@MergeSubcomponent is not allowed`() = testFactory {
    compile(
      """
      package com.squareup.test
      
      import dagger.Subcomponent
      
      @com.squareup.anvil.annotations.MergeSubcomponent(Any::class)
      class AnyClass
      """,
      disableComponentMerging = true,
    ) {
      assertError()
    }
  }

  @TestFactory
  fun `@MergeModules is not allowed`() = testFactory {
    compile(
      """
      package com.squareup.test
      
      import dagger.Subcomponent
      
      @com.squareup.anvil.annotations.compat.MergeModules(Any::class)
      class AnyClass
      """,
      disableComponentMerging = true,
    ) {
      assertError()
    }
  }

  @TestFactory
  fun `@MergeInterfaces is not allowed`() = testFactory {
    compile(
      """
      package com.squareup.test
      
      import dagger.Subcomponent
      
      @com.squareup.anvil.annotations.compat.MergeInterfaces(Any::class)
      class AnyClass
      """,
      disableComponentMerging = true,
    ) {
      assertError()
    }
  }

  private fun JvmCompilationResult.assertError() {
    assertThat(exitCode).isError()
    assertThat(messages).contains("Source0.kt:6")
    assertThat(messages).contains(
      "This Gradle module is configured to ONLY generate code with the " +
        "`disableComponentMerging` flag. However, this module contains code that uses " +
        "Anvil @Merge* annotations. That's not supported.",
    )
  }

  // private fun compile(
  //   @Language("kotlin") vararg sources: String,
  //   mode: AnvilCompilationMode,
  //   block: JvmCompilationResult.() -> Unit = { },
  // ): JvmCompilationResult = compileAnvil(
  //   sources = sources,
  //   disableComponentMerging = true,
  //   allWarningsAsErrors = WARNINGS_AS_ERRORS,
  //   block = block,
  //   mode = mode,
  // )
}
