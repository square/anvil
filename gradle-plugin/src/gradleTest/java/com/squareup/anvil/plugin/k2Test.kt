package com.squareup.anvil.plugin

import com.rickbusarow.kase.gradle.dsl.buildFile
import com.squareup.anvil.plugin.testing.BaseGradleTest
import com.squareup.anvil.plugin.testing.classGraphResult
import io.github.classgraph.AnnotationClassRef
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.TestFactory

class K2Test : BaseGradleTest() {

  @TestFactory
  fun `canary test`() = testFactory {

    rootProject {

      buildFile {
        plugins {
          kotlin("jvm")
          id("com.squareup.anvil")
          // kotlin("kapt")
        }

        raw(
          """
          kotlin {
            compilerOptions {
              languageVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0
            }
          }
          """.trimIndent(),
        )

        // anvil {
        //   generateDaggerFactories.set(true)
        // }

        dependencies {
          compileOnly(libs.inject)
          api(libs.dagger2.annotations)
          // kapt(libs.dagger2.compiler)
        }
      }

      gradlePropertiesFile(
        """
        kapt.use.k2=true
        """.trimIndent(),
      )

      dir("src/main/java") {
        kotlinFile(
          "foo/targets.kt",
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
          """.trimIndent(),
        )
      }
    }

    shouldSucceed("jar") {
      val classGraph = rootProject.classGraphResult()

      val testComponent = classGraph.getClassInfo("foo.TestComponent")
        .shouldNotBeNull()

      val generatedComponentAnnotation = testComponent.getAnnotationInfo("dagger.Component")

      val moduleClasses = generatedComponentAnnotation.parameterValues
        .getValue("modules")
        .let { it as Array<*> }
        .map { (it as AnnotationClassRef).name }
        .sorted()

      moduleClasses shouldBe listOf(
        "foo.ABindingModule",
        "foo.BBindingModule",
      )

      val daggerTestComponent = classGraph.getClassInfo("foo.DaggerTestComponent")
        .shouldNotBeNull()

      val testComponentImpl = daggerTestComponent.innerClasses
        .get("foo.DaggerTestComponent\$TestComponentImpl")

      testComponentImpl.methodInfo.map { it.name } shouldBe listOf("injectClass", "getB")
    }
  }
}
