package com.squareup.anvil.plugin

import com.rickbusarow.kase.gradle.dsl.buildFile
import com.squareup.anvil.plugin.testing.BaseGradleTest
import org.junit.jupiter.api.TestFactory

class k2Test : BaseGradleTest() {

  @TestFactory
  fun `canary test`() = testFactory {

    rootProject {

      buildFile {
        plugins {
          kotlin("jvm")
          id("com.squareup.anvil")
          kotlin("kapt")
        }

        // anvil {
        //   generateDaggerFactories.set(true)
        // }

        dependencies {
          compileOnly(libs.inject)
          api(libs.dagger2.annotations)
          kapt(libs.dagger2.compiler)
        }
      }

      dir("src/main/java") {
        kotlinFile(
          "foo/targets.kt",
          """
          package foo
  
          import dagger.Binds
          import dagger.Component
          import dagger.Module
          import dagger.Subcomponent
          import kotlin.reflect.KClass
          import javax.inject.Inject
  
          @MergeComponentFir
          @Component( modules = [ABindingModule::class] )
          interface TestComponent
  
          interface ComponentBase {
            // val b: B
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
          """.trimIndent(),
        )
      }
      gradlePropertiesFile(
        """
          org.gradle.caching=false
          com.squareup.anvil.trackSourceFiles=true
          kapt.use.k2=true
          kapt.verbose=true
          kotlin.verbose=true

          # Enable compilation of project using FIR compiler
          kotlin.build.useFir=true
          # Enable FIR compiler for kotlin-stdlib, kotlin-reflect, kotlin-test.
          kotlin.build.useFirForLibraries=true

        """.trimIndent(),
      )
    }

    shouldSucceed("jar", "--dry-run") {
      // rootProject.generatedDir(useKsp = false).injectClassFactory.shouldExist()
    }

    workingDir.resolve("build").deleteRecursivelyOrFail()
  }
}
