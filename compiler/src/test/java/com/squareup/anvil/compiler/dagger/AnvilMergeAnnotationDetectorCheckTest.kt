package com.squareup.anvil.compiler.dagger

import com.google.common.truth.Truth.assertThat
import com.sqareup.anvil.compiler.internal.testing.compileAnvil
import com.squareup.anvil.compiler.USE_IR
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.COMPILATION_ERROR
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.OK
import com.tschuchort.compiletesting.KotlinCompilation.Result
import org.junit.Test

class AnvilMergeAnnotationDetectorCheckTest {

  @Test fun `@ContributesTo is allowed`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.Module
      
      @Module
      @com.squareup.anvil.annotations.ContributesTo(Any::class)
      object AnyClass
      """
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
      """
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
    assertThat(exitCode).isEqualTo(COMPILATION_ERROR)
    assertThat(messages).contains("Source.kt: (5, 1")
    assertThat(messages).contains(
      "This Gradle module is configured to ONLY generate code with the " +
        "`disableComponentMerging` flag. However, this module contains code that uses " +
        "Anvil @Merge* annotations. That's not supported."
    )
  }

  @Suppress("CHANGING_ARGUMENTS_EXECUTION_ORDER_FOR_NAMED_VARARGS")
  private fun compile(
    vararg sources: String,
    block: Result.() -> Unit = { }
  ): Result = compileAnvil(
    sources = sources,
    disableComponentMerging = true,
    useIR = USE_IR,
    block = block
  )
}
