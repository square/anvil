package com.squareup.anvil.compiler.dagger

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.compiler.internal.testing.AnvilCompilationMode
import com.squareup.anvil.compiler.internal.testing.compileAnvil
import com.squareup.anvil.compiler.testParams
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.OK
import org.intellij.lang.annotations.Language
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
class BindsMethodValidatorTest(
  private val useDagger: Boolean,
  private val mode: AnvilCompilationMode,
) {

  companion object {
    @Parameters(name = "Use Dagger: {0}, mode: {1}")
    @JvmStatic
    fun params() = testParams()
  }

  @Test
  fun `a binding with an incompatible parameter type fails to compile`() {
    compile(
      """
      package com.squareup.test
 
      import dagger.Binds
      import dagger.Module
      import javax.inject.Inject

      interface Lorem
      open class Ipsum
      class Foo @Inject constructor() : Ipsum(), Lorem
      interface Bar

      @Module
      abstract class BarModule {
        @Binds
        abstract fun bindsBar(impl: Foo): Bar
      }
      """,
      expectExitCode = KotlinCompilation.ExitCode.COMPILATION_ERROR,
    ) {
      assertThat(messages).contains(
        "@Binds methods' parameter type must be assignable to the return type",
      )
      if (!useDagger) {
        assertThat(messages).contains(
          "Expected binding of type com.squareup.test.Bar but impl parameter of type com.squareup.test.Foo only has the following " +
            "supertypes: [com.squareup.test.Ipsum, com.squareup.test.Lorem]",
        )
      }
    }
  }

  @Test
  fun `a binding with an incompatible parameter type with no supertypes fails to compile`() {
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
      """,
      expectExitCode = KotlinCompilation.ExitCode.COMPILATION_ERROR,
    ) {
      assertThat(messages).contains(
        "@Binds methods' parameter type must be assignable to the return type",
      )
      if (!useDagger) {
        assertThat(messages).contains(
          "Expected binding of type com.squareup.test.Bar but impl parameter of type com.squareup.test.Foo has no supertypes.",
        )
      }
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
      """,
      expectExitCode = KotlinCompilation.ExitCode.COMPILATION_ERROR,
    ) {
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
      """,
      expectExitCode = KotlinCompilation.ExitCode.COMPILATION_ERROR,
    ) {
      assertThat(messages).contains(
        "@Binds methods must have exactly one parameter, " +
          "whose type is assignable to the return type",
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
      """,
      expectExitCode = KotlinCompilation.ExitCode.COMPILATION_ERROR,
    ) {
      assertThat(messages).contains(
        "@Binds methods must have exactly one parameter, " +
          "whose type is assignable to the return type",
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
      """,
      expectExitCode = KotlinCompilation.ExitCode.COMPILATION_ERROR,
    ) {
      assertThat(messages).contains(
        "@Binds methods must return a value (not void)",
      )
    }
  }

  @Test
  fun `an extension function binding is invalid`() {
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
        abstract fun Foo.bindsBar(): Bar
      }
      """,
      expectExitCode = KotlinCompilation.ExitCode.COMPILATION_ERROR,
    ) {
      assertThat(messages).contains("@Binds methods can not be an extension function")
    }
  }

  @Test
  fun `a binding for a back-ticked-package type is valid`() {
    val moduleResult = compile(
      """
      package com.squareup.`impl`

      interface Bar
      """,
      """
      package com.squareup.test
 
      import dagger.Binds
      import dagger.Module
      import javax.inject.Inject

      class Foo @Inject constructor() : com.squareup.`impl`.Bar

      @Module
      abstract class BarModule {
        @Binds
        abstract fun bindsBar(foo: Foo): com.squareup.`impl`.Bar
      }
      """,
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }

    compile(
      """
      package com.squareup.test

      import dagger.Component
      import javax.inject.Singleton

      @Component(modules = [BarModule::class])
      interface ComponentInterface {
        fun bar(): com.squareup.`impl`.Bar
      }
      """,
      previousCompilationResult = moduleResult,
      enableDagger = true,
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }

  @Test
  fun `an extension function binding with a qualifier is invalid`() {
    compile(
      """
      package com.squareup.test
 
      import dagger.Binds
      import dagger.Module
      import dagger.Provides
      import javax.inject.Inject
      import javax.inject.Qualifier

      @Qualifier
      annotation class Marker

      class Foo : Bar
      interface Bar

      @Module
      abstract class BarModule {

        @Marker
        @Binds
        abstract fun @receiver:Marker Foo.bindsBar(): Bar

        companion object {
            @Provides 
            @Marker
            fun providesFoo(): Foo = Foo()
        }
      }
      """,
      expectExitCode = KotlinCompilation.ExitCode.COMPILATION_ERROR,
    ) {
      assertThat(messages).contains("@Binds methods can not be an extension function")
    }
  }

  @Test
  fun `binding inside interface is valid`() {
    compile(
      """
      package com.squareup.test

      import dagger.Binds
      import dagger.Module
      import javax.inject.Inject

      interface Foo : Bar
      interface Bar

      @Module
      interface BarModule {
        @Binds
        fun bindsBar(foo: Foo): Bar
      }
      """,
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }

  private fun compile(
    @Language("kotlin") vararg sources: String,
    previousCompilationResult: JvmCompilationResult? = null,
    enableDagger: Boolean = useDagger,
    expectExitCode: KotlinCompilation.ExitCode = OK,
    block: JvmCompilationResult.() -> Unit = { },
  ): JvmCompilationResult = compileAnvil(
    sources = sources,
    previousCompilationResult = previousCompilationResult,
    expectExitCode = expectExitCode,
    enableDaggerAnnotationProcessor = enableDagger,
    generateDaggerFactories = !enableDagger,
    mode = mode,
    block = block,
  )
}
