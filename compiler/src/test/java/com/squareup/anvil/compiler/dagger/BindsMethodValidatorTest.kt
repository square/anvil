package com.squareup.anvil.compiler.dagger

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.compiler.WARNINGS_AS_ERRORS
import com.squareup.anvil.compiler.daggerProcessingModesForTests
import com.squareup.anvil.compiler.internal.testing.DaggerAnnotationProcessingMode
import com.squareup.anvil.compiler.internal.testing.compileAnvil
import com.squareup.anvil.compiler.isError
import com.squareup.anvil.compiler.testIsNotYetCompatibleWithKsp
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.OK
import org.intellij.lang.annotations.Language
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
class BindsMethodValidatorTest(
  private val daggerProcessingMode: DaggerAnnotationProcessingMode?
) {

  companion object {
    @Parameters(name = "Dagger processing mode: {0}")
    @JvmStatic
    fun daggerProcessingModes() = daggerProcessingModesForTests()
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
      """
    ) {
      assertThat(exitCode).isError()
      assertThat(messages).contains(
        "@Binds methods' parameter type must be assignable to the return type"
      )
      if (daggerProcessingMode == null) {
        assertThat(messages).contains(
          "Expected binding of type Bar but impl parameter of type Foo only has the following " +
            "supertypes: [Ipsum, Lorem]"
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
      """
    ) {
      assertThat(exitCode).isError()
      assertThat(messages).contains(
        "@Binds methods' parameter type must be assignable to the return type"
      )
      if (daggerProcessingMode == null) {
        assertThat(messages).contains(
          "Expected binding of type Bar but impl parameter of type Foo has no supertypes."
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
  fun `an extension function binding with a parameter fails to compile`() {
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
        abstract fun Foo.bindsBar(impl2: Hammer): Bar
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

  @Test
  fun `an extension function binding is valid`() {
    assumeTrue(daggerProcessingMode != null)
    val moduleResult = compile(
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
      """
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
        fun bar(): Bar
      }
      """,
      previousCompilationResult = moduleResult,
      daggerProcessingMode = daggerProcessingMode,
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }

  @Test
  fun `a binding for a back-ticked-package type is valid`() {
    assumeTrue(daggerProcessingMode != null)
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
      daggerProcessingMode = daggerProcessingMode,
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }

  @Test
  fun `an extension function binding with a qualifier is valid`() {
    assumeTrue(daggerProcessingMode != null)
    testIsNotYetCompatibleWithKsp(
      daggerProcessingMode,
      "https://github.com/google/dagger/issues/3990"
    )
    val moduleResult = compile(
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
      """
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }

    compile(
      """
      package com.squareup.test

      import dagger.Component

      @Component(modules = [BarModule::class])
      interface ComponentInterface {
        @Marker
        fun bar(): Bar
      }
      """,
      previousCompilationResult = moduleResult,
      daggerProcessingMode = daggerProcessingMode,
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }

  private fun compile(
    @Language("kotlin") vararg sources: String,
    previousCompilationResult: JvmCompilationResult? = null,
    daggerProcessingMode: DaggerAnnotationProcessingMode? = null,
    block: JvmCompilationResult.() -> Unit = { }
  ): JvmCompilationResult = compileAnvil(
    sources = sources,
    daggerAnnotationProcessingMode = daggerProcessingMode,
    generateDaggerFactories = daggerProcessingMode == null,
    allWarningsAsErrors = WARNINGS_AS_ERRORS,
    previousCompilationResult = previousCompilationResult,
    block = block
  )
}
