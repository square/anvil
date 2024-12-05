package com.squareup.anvil.compiler.dagger

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.compiler.api.CodeGenerator
import com.squareup.anvil.compiler.internal.testing.AnvilCompilationMode
import com.squareup.anvil.compiler.internal.testing.compileAnvil
import com.squareup.anvil.compiler.isError
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.OK
import org.intellij.lang.annotations.Language
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class AnvilMergeAnnotationDetectorCheckTest(
  private val mode: AnvilCompilationMode,
) {

  companion object {
    @Parameterized.Parameters(name = "{0}")
    @JvmStatic
    fun modes(): Collection<Any> {
      return listOf(AnvilCompilationMode.Embedded())
    }
  }

  @Test fun `@ContributesTo is allowed`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.Module
      
      @Module
      @com.squareup.anvil.annotations.ContributesTo(Any::class)
      object AnyClass
      """,
      mode = mode,
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }

  @Test fun `@ContributesBinding is allowed`() {
    compile(
      """
      package com.squareup.test
      
      interface BaseType
      
      @com.squareup.anvil.annotations.ContributesBinding(Any::class)
      class AnyClass : BaseType
      """,
      mode = mode,
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }

  @Test fun `@MergeComponent is not allowed`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.Subcomponent
      
      @com.squareup.anvil.annotations.MergeComponent(Any::class)
      class AnyClass
      """,
      mode = mode,
    ) {
      assertError()
    }
  }

  @Test fun `@MergeSubcomponent is not allowed`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.Subcomponent
      
      @com.squareup.anvil.annotations.MergeSubcomponent(Any::class)
      class AnyClass
      """,
      mode = mode,
    ) {
      assertError()
    }
  }

  @Test fun `@MergeModules is not allowed`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.Subcomponent
      
      @com.squareup.anvil.annotations.compat.MergeModules(Any::class)
      class AnyClass
      """,
      mode = mode,
    ) {
      assertError()
    }
  }

  @Test fun `@MergeInterfaces is not allowed`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.Subcomponent
      
      @com.squareup.anvil.annotations.compat.MergeInterfaces(Any::class)
      class AnyClass
      """,
      mode = mode,
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

  private fun compile(
    @Language("kotlin") vararg sources: String,
    codeGenerators: List<CodeGenerator> = emptyList(),
    mode: AnvilCompilationMode = AnvilCompilationMode.Embedded(codeGenerators),
    block: JvmCompilationResult.() -> Unit = { },
  ): JvmCompilationResult = compileAnvil(
    sources = sources,
    disableComponentMerging = true,
    block = block,
    mode = mode,
  )
}
