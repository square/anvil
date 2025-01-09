package com.squareup.anvil.compiler.k2.codegen

import com.rickbusarow.kase.stdlib.createSafely
import com.squareup.anvil.compiler.internal.testing.k2.CompilationMode
import com.squareup.anvil.compiler.internal.testing.k2.CompilationModeTest
import com.squareup.anvil.compiler.internal.testing.k2.compile2
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.TestFactory

class FirCanaryTest : CompilationModeTest(
  CompilationMode.K2(useKapt = false),
  CompilationMode.K2(useKapt = true),
) {

  val targets = //language=kotlin
    """
    package foo

    import com.squareup.anvil.annotations.ContributesTo
    import com.squareup.anvil.annotations.MergeComponent
    import dagger.Binds
    import dagger.Component
    import dagger.Module
    import dagger.Subcomponent
    import kotlin.reflect.KClass
    import javax.inject.Inject

    @MergeComponent(Unit::class, modules = [ABindingModule::class])
    interface TestComponent {
      val b: B
    }

    interface ComponentBase {
      fun injectClass(): InjectClass
    }

    @Module
    interface ABindingModule {
      @Binds
      fun bindAImpl(aImpl: AImpl): A
    }

    @Module
    @ContributesTo(Unit::class)
    interface BBindingModule {
      @Binds
      fun bindBImpl(bImpl: BImpl): B
    }
    
    interface A
    class AImpl @Inject constructor() : A
    
    interface B
    class BImpl @Inject constructor() : B

    class InjectClass @Inject constructor(val a: A, val b: B)
    """.trimIndent()

  @TestFactory
  fun `compile2 version canary`() = params
    .asTests {

      val srcFile = workingDir.resolve("foo/targets.kt")
        .createSafely(targets)

      compile2(listOf(srcFile)) shouldBe true
    }
}
