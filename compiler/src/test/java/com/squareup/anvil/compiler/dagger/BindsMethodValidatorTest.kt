package com.squareup.anvil.compiler.dagger

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.compiler.USE_IR
import com.squareup.anvil.compiler.WARNINGS_AS_ERRORS
import com.squareup.anvil.compiler.internal.testing.compileAnvil
import com.squareup.anvil.compiler.isError
import com.squareup.anvil.compiler.isFullTestRun
import com.tschuchort.compiletesting.KotlinCompilation.Result
import org.intellij.lang.annotations.Language
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@Suppress("UNCHECKED_CAST")
@RunWith(Parameterized::class)
class BindsMethodValidatorTest(
  private val useDagger: Boolean
) {

  companion object {
    @Parameters(name = "Use Dagger: {0}")
    @JvmStatic fun useDagger(): Collection<Any> {
      return listOf(isFullTestRun(), false).distinct()
    }
  }

  @Test
  fun `a binding with an incompatible parameter type fails to compile`() {
    compile(
      """
      package com.squareup.test
 
      import dagger.Binds
      import dagger.Module
      import javax.inject.Inject

      class Foo @Inject constructor()
      interface Bar

      @Module
      abstract class BarModule {
        @Binds
        abstract fun bindsBar(impl: Foo): Bar
      }
      """
    ) {
      assertThat(exitCode).isError()
      assertThat(messages).contains(
        "@Binds methods' parameter type must be assignable to the return type"
      )
    }
  }

  @Test
  fun `a non-abstract binding fails to compile`() {
    compile(
      """
      package com.squareup.test
 
      import dagger.Binds
      import dagger.Module
      import javax.inject.Inject

      class Foo @Inject constructor() : Bar
      interface Bar

      @Module
      class BarModule {
        @Binds
        fun bindsBar(impl: Foo): Bar {
          return impl
        }
      }
      """
    ) {
      assertThat(exitCode).isError()
      assertThat(messages).contains("@Binds methods must be abstract")
    }
  }

  @Test
  fun `a binding with no parameters fails to compile`() {
    compile(
      """
      package com.squareup.test
 
      import dagger.Binds
      import dagger.Module
      import javax.inject.Inject

      class Foo @Inject constructor() : Bar
      interface Bar

      @Module
      abstract class BarModule {
        @Binds
        abstract fun bindsBar(): Bar
      }
      """
    ) {
      assertThat(exitCode).isError()
      assertThat(messages).contains(
        "@Binds methods must have exactly one parameter, " +
          "whose type is assignable to the return type"
      )
    }
  }

  @Test
  fun `a binding with multiple parameters fails to compile`() {
    compile(
      """
      package com.squareup.test
 
      import dagger.Binds
      import dagger.Module
      import javax.inject.Inject

      class Foo @Inject constructor() : Bar
      class Hammer @Inject constructor() : Bar
      interface Bar

      @Module
      abstract class BarModule {
        @Binds
        abstract fun bindsBar(impl1: Foo, impl2: Hammer): Bar
      }
      """
    ) {
      assertThat(exitCode).isError()
      assertThat(messages).contains(
        "@Binds methods must have exactly one parameter, " +
          "whose type is assignable to the return type"
      )
    }
  }

  @Test
  fun `a binding with no return type fails to compile`() {
    compile(
      """
      package com.squareup.test
 
      import dagger.Binds
      import dagger.Module
      import javax.inject.Inject

      class Foo @Inject constructor() : Bar
      interface Bar

      @Module
      abstract class BarModule {
        @Binds
        abstract fun bindsBar(impl1: Foo)
      }
      """
    ) {
      assertThat(exitCode).isError()
      assertThat(messages).contains(
        "@Binds methods must return a value (not void)"
      )
    }
  }

  private fun compile(
    @Language("kotlin") vararg sources: String,
    previousCompilationResult: Result? = null,
    block: Result.() -> Unit = { }
  ): Result = compileAnvil(
    sources = sources,
    enableDaggerAnnotationProcessor = useDagger,
    generateDaggerFactories = !useDagger,
    useIR = USE_IR,
    allWarningsAsErrors = WARNINGS_AS_ERRORS,
    previousCompilationResult = previousCompilationResult,
    block = block
  )
}
