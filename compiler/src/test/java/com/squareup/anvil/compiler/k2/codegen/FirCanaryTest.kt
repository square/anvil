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

    import dagger.Binds
    import dagger.Component
    import dagger.Module
    import dagger.Subcomponent
    import kotlin.reflect.KClass
    import javax.inject.Inject

    @MergeComponentFir(
       modules =       [foo.ABindingModule::class],
       dependencies =  [DependencyComponent::class],
    )
    interface TestComponent

    @Component
    interface DependencyComponent

    interface ComponentBase {
      val a: A
      val b: B
    }

    @Module
    interface ABindingModule {
      @Binds
      fun bindAImpl(aImpl: AImpl): A
    }

    @Module
    interface BBindingModule {
      @Binds
      fun bindBImpl(bImpl: BImpl): B
    }

    class InjectClass @Inject constructor(
      val a: A,
      val b: B
    )
    
    interface A
    class AImpl @Inject constructor() : A
    
    interface B
    class BImpl @Inject constructor(val a: A) : B

    annotation class MergeComponentFir(
      val modules: Array<KClass<*>>,
      val dependencies: Array<KClass<*>>
    )
    """.trimIndent()

  @TestFactory
  fun `compile2 version canary`() = params
    .asTests {

      val srcFile = workingDir.resolve("foo/targets.kt")
        .createSafely(targets)

      compile2(listOf(srcFile)) shouldBe true
    }
}
