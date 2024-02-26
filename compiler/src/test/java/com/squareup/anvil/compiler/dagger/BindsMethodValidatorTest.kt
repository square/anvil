package com.squareup.anvil.compiler.dagger

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.compiler.WARNINGS_AS_ERRORS
import com.squareup.anvil.compiler.internal.testing.compileAnvil
import com.squareup.anvil.compiler.isError
import com.squareup.anvil.compiler.isFullTestRun
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.COMPILATION_ERROR
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.OK
import org.intellij.lang.annotations.Language
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
class BindsMethodValidatorTest(
  private val useDagger: Boolean,
) {

  companion object {
    @Parameters(name = "Use Dagger: {0}")
    @JvmStatic
    fun useDagger(): Collection<Any> {
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
    ) {
      assertThat(exitCode).isError()
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
    ) {
      assertThat(exitCode).isError()
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
  fun `a bound type with a compatible generic type parameter is allowed`() {
    compile(
      """
      package com.squareup.test
 
      import dagger.Binds
      import dagger.Module
import java.lang.Void
      import javax.inject.Inject

      class Foo @Inject constructor() : Bar<Unit>
      interface Bar<T>

      @Module
      abstract class BarModule {
        @Binds
        abstract fun bindsBar(impl: Foo): Bar<*>
      }
      """,
    ) {
      assertThat(exitCode).isError()
      assertThat(messages).contains("@Binds methods must be abstract")
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
      """,
    ) {
      assertThat(exitCode).isError()
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
    ) {
      assertThat(exitCode).isError()
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
    ) {
      assertThat(exitCode).isError()
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
    ) {
      assertThat(exitCode).isEqualTo(COMPILATION_ERROR)
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
    ) {
      assertThat(exitCode).isEqualTo(COMPILATION_ERROR)
      assertThat(messages).contains("@Binds methods can not be an extension function")
    }
  }

  @Test
  fun `binding which supertype is narrower than return type fails to compile`() {
    compile(
      """
        package com.squareup.test

        import dagger.Module
        import dagger.Binds
    
        sealed interface ItemDetail {
          object DetailTypeA : ItemDetail
        }

        interface ItemMapper<T : ItemDetail>

        class DetailTypeAItemMapper : ItemMapper<ItemDetail.DetailTypeA>
        
        @Module
        interface SomeModule {
          @Binds fun shouldBeInvalidComplexBinding(real: DetailTypeAItemMapper): ItemMapper<ItemDetail>
        }
      """.trimIndent(),
    ) {
      assertThat(exitCode).isEqualTo(COMPILATION_ERROR)
      assertThat(messages).contains(
        "@Binds methods' parameter type must be assignable to the return type",
      )
      if (!useDagger) {
        assertThat(messages).contains(
          "@Binds methods' parameter type must be assignable to the return type. Expected " +
            "binding of type com.squareup.test.ItemMapper<com.squareup.test.ItemDetail> but impl " +
            "parameter of type com.squareup.test.DetailTypeAItemMapper only has the following " +
            "supertypes: [com.squareup.test.ItemMapper<com.squareup.test.ItemDetail.DetailTypeA>]",
        )
      }
    }
  }

  private fun compile(
    @Language("kotlin") vararg sources: String,
    previousCompilationResult: JvmCompilationResult? = null,
    enableDagger: Boolean = useDagger,
    block: JvmCompilationResult.() -> Unit = { },
  ): JvmCompilationResult = compileAnvil(
    sources = sources,
    enableDaggerAnnotationProcessor = enableDagger,
    generateDaggerFactories = !enableDagger,
    allWarningsAsErrors = WARNINGS_AS_ERRORS,
    previousCompilationResult = previousCompilationResult,
    block = block,
  )
}
