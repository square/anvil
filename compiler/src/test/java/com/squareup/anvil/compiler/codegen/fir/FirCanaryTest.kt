package com.squareup.anvil.compiler.codegen.fir

import com.rickbusarow.kase.stdlib.createSafely
import com.squareup.anvil.compiler.fir.internal.compile2
import com.squareup.anvil.compiler.internal.testing.AnvilCompilationMode
import com.squareup.anvil.compiler.testing.AnvilCompilationModeTest
import com.tschuchort.compiletesting.KotlinCompilation
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

class FirCanaryTest : AnvilCompilationModeTest(AnvilCompilationMode.Embedded()) {

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
      modules = [ABindingModule::class, EmptyModule::class]
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
    interface EmptyModule {
      @Binds
      fun bindBImpl(bImpl: BImpl): B
    }

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
  fun `compile2 version canary`() = testFactory {

    compile2(
      workingDir,
      listOf(
        workingDir.resolve("foo/targets.kt").createSafely(targets),
      ),
    ) shouldBe true
  }

  @TestFactory
  fun `kct version canary`() = testFactory {

    compile(
      targets,
      enableDaggerAnnotationProcessor = true,
      allWarningsAsErrors = false,
      useK2 = true,
    ) {

      exitCode shouldBe KotlinCompilation.ExitCode.OK
    }
  }

  @Test
  fun `everything is done!!!`() {
    val todo =
      //language=markdown
      """
      []- `@Module` merging
  []- `@MergeComponent`, `@MergeModules`, `@MergeSubcomponent`
  []- Merged arguments are represented in the Dagger-generated stubs
    ```kotlin
    @Component(modules = [Module1::class, Module2::class])
    interface AppComponent
    ```
  []- kapt stub generation -> kotlin compile -> kapt lowering(What's lowering?)
What are the gradle tasks and what runs in each task.
Outline kapt3 + k1 anvil vs Outline kapt4 + k2 anvil.


```mermaid
graph TD
  start("./gradlew build")
  
  kaptGenerateStubs[":kaptGenerateStubs (creates front-end)"]
  compileKotlin[":compileKotlin (includes FIR generation)"]
  kaptSomething[":kaptSomething (creates actual implementations)"]
  
  compileJava[":compileJava"]
  
  jar[":jar"]
  
  finish("Gradle build completed")
  
  start --> kaptGenerateStubs

  kaptGenerateStubs --> compileKotlin
  
  compileKotlin --> kaptSomething
  
  kaptSomething --> compileJava
  
  compileJava --> jar
  
  jar --> finish
  
```

      """.trimIndent()

    todo.lines().count { it.startsWith("[]") } shouldBe 0
  }
}
