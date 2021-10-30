package com.squareup.anvil.compiler.dagger

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.compiler.USE_IR
import com.squareup.anvil.compiler.WARNINGS_AS_ERRORS
import com.squareup.anvil.compiler.internal.testing.compileAnvil
import com.squareup.anvil.compiler.isError
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.OK
import com.tschuchort.compiletesting.KotlinCompilation.Result
import org.intellij.lang.annotations.Language
import org.junit.Test

class AnvilAnnotationDetectorCheckTest {

  @Test fun `a Dagger subcomponent is allowed`() {
    compile(
      """
      package com.squareup.test
      
      @dagger.Subcomponent
      interface ComponentInterface
      """
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }

  @Test fun `@ContributesTo is not allowed`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.Subcomponent
      
      @com.squareup.anvil.annotations.ContributesTo(Any::class)
      class AnyClass
      """
    ) {
      assertError()
    }
  }

  @Test fun `@ContributesBinding is not allowed`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.Subcomponent
      
      @com.squareup.anvil.annotations.ContributesBinding(Any::class)
      class AnyClass
      """
    ) {
      assertError()
    }
  }

  @Test fun `@MergeComponent is not allowed`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.Subcomponent
      
      @com.squareup.anvil.annotations.MergeComponent(Any::class)
      class AnyClass
      """
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
      """
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
      """
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
      """
    ) {
      assertError()
    }
  }

  private fun Result.assertError() {
    assertThat(exitCode).isError()
    assertThat(messages).contains("Source0.kt: (5, 1")
    assertThat(messages).contains(
      "This Gradle module is configured to ONLY generate Dagger factories with the " +
        "`generateDaggerFactoriesOnly` flag. However, this module contains code that uses " +
        "other Anvil annotations. That's not supported."
    )
  }

  private fun compile(
    @Language("kotlin") vararg sources: String,
    block: Result.() -> Unit = { }
  ): Result = compileAnvil(
    sources = sources,
    generateDaggerFactories = true,
    generateDaggerFactoriesOnly = true,
    useIR = USE_IR,
    allWarningsAsErrors = WARNINGS_AS_ERRORS,
    block = block
  )
}
