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

    @MergeComponentFir
    @Component(
      modules = [ABindingModule::class],
    )
    interface TestComponent

    interface ComponentBase {
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

    @Module
    interface EmptyModule

    class InjectClass @Freddy constructor()
    
    class OtherClass @Inject constructor()
    
    interface A
    class AImpl @Inject constructor() : A
    
    interface B
    class BImpl @Inject constructor(val a: A) : B

    annotation class Freddy
    annotation class MergeComponentFir
    annotation class ComponentKotlin(val modules: Array<KClass<*>>)
    """.trimIndent()

  @TestFactory
  fun `compile2 version canary`() = params
    .filter { (mode) -> !mode.useKapt }
    .asTests {

      compile2(listOf(workingDir.resolve("foo/targets.kt").createSafely(targets))) shouldBe true
    }

  @TestFactory
  fun `compile2 with a string argument`() = testFactory {

    compile2(
      """
      package squareup.test

      import javax.inject.Inject

      class InjectClass @Inject constructor()
      """.trimIndent(),
    ) shouldBe true
  }
}
