package com.squareup.anvil.compiler.dagger

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.compiler.WARNINGS_AS_ERRORS
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
class ComponentDetectorCheckTest(
  private val mode: AnvilCompilationMode,
) {

  companion object {
    @Parameterized.Parameters(name = "{0}")
    @JvmStatic
    fun modes(): Collection<Any> {
      return listOf(AnvilCompilationMode.Embedded(), AnvilCompilationMode.Ksp())
    }
  }

  @Test fun `a Dagger component causes an error`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.Component
      
      @Component
      interface ComponentInterface
      """,
      mode = mode,
    ) {
      assertThat(exitCode).isError()
      // Position to the class. )
      assertThat(messages).contains("Source0.kt:6")
      assertThat(messages).contains(
        "Anvil cannot generate the code for Dagger components or subcomponents. In these " +
          "cases the Dagger annotation processor is required. Enabling the Dagger " +
          "annotation processor and turning on Anvil to generate Dagger factories is " +
          "redundant. Set 'generateDaggerFactories' to false.",
      )
    }
  }

  @Test fun `a Dagger subcomponent is allowed`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.Subcomponent
      
      @Subcomponent
      interface ComponentInterface
      """,
      mode = mode,
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }

  @Test fun `a Dagger component causes an error inner class`() {
    compile(
      """
        package com.squareup.test
        
        import dagger.Component
        
        class OuterClass {
          @Component
          interface ComponentInterface
        }
        """,
      mode = mode,
    ) {
      assertThat(exitCode).isError()
      // Position to the class.
      assertThat(messages).contains("Source0.kt:7")
      assertThat(messages).contains(
        "Anvil cannot generate the code for Dagger components or subcomponents. In these " +
          "cases the Dagger annotation processor is required. Enabling the Dagger " +
          "annotation processor and turning on Anvil to generate Dagger factories is " +
          "redundant. Set 'generateDaggerFactories' to false.",
      )
    }
  }

  @Test fun `a Dagger subcomponent in an inner class is allowed`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.Subcomponent
      
      class OuterClass {
        @Subcomponent
        interface ComponentInterface
      }
      """,
      mode = mode,
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }

  private fun compile(
    @Language("kotlin") vararg sources: String,
    codeGenerators: List<CodeGenerator> = emptyList(),
    mode: AnvilCompilationMode = AnvilCompilationMode.Embedded(codeGenerators),
    block: JvmCompilationResult.() -> Unit = { },
  ): JvmCompilationResult = compileAnvil(
    sources = sources,
    generateDaggerFactories = true,
    allWarningsAsErrors = WARNINGS_AS_ERRORS,
    block = block,
    mode = mode,
  )
}
