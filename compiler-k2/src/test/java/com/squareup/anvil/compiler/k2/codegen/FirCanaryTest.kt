package com.squareup.anvil.compiler.k2.codegen

import com.rickbusarow.kase.stdlib.createSafely
import com.squareup.anvil.compiler.testing.CompilationMode
import com.squareup.anvil.compiler.testing.CompilationModeTest
import com.squareup.anvil.compiler.testing.compile2
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

    @Module
    @ContributesTo(Unit::class)
    interface BBindingModule {
      @Binds
      fun bindBImpl(bImpl: BImpl): B
    }
    
    @MergeComponent(Unit::class, modules = [ABindingModule::class])
    interface TestComponent {
      val b: B
    }

    @ContributesTo(Unit::class)
    interface ComponentBase {
      fun injectClass(): InjectClass
    }

    @dagger.Component
    interface OtherComponent

    @Module
    interface ABindingModule {
      @Binds
      fun bindAImpl(aImpl: AImpl): A
    }

    interface A
    class AImpl @Inject constructor() : A
    
    interface B
    class BImpl @Inject constructor() : B

    class InjectClass @Inject constructor(val a: A, val b: B)
    """.trimIndent()

  @TestFactory
  fun `compile2 version canary`() = testFactory {

    val srcFile = workingDir.resolve("foo/targets.kt")
      .createSafely(targets)

    compile2(listOf(srcFile)) shouldBe true
  }

  @TestFactory
  fun `top-level file generation`() = params
    .filter { (mode) -> !mode.useKapt }
    .asTests {

      compile2(
        """
        package foo
    
        import javax.inject.Inject
    
        class InjectClass @Inject constructor(val name: String)
        """.trimIndent(),
      ) shouldBe true
    }
}
