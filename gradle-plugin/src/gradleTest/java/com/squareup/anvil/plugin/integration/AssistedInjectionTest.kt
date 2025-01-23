package com.squareup.anvil.plugin.integration

import com.rickbusarow.kase.gradle.dsl.buildFile
import com.squareup.anvil.plugin.testing.FactoryGenTest
import com.squareup.anvil.plugin.testing.allDaggerFactoryClasses
import com.squareup.anvil.plugin.testing.allModuleClasses
import com.squareup.anvil.plugin.testing.allProvidesMethods
import com.squareup.anvil.plugin.testing.anvil
import com.squareup.anvil.plugin.testing.appComponent
import com.squareup.anvil.plugin.testing.classGraphResult
import com.squareup.anvil.plugin.testing.daggerAppComponent
import com.squareup.anvil.plugin.testing.names
import io.kotest.assertions.asClue
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.TestFactory

class CodeGeneratorProjectTests : FactoryGenTest() {
  @TestFactory
  fun `i dunno`() = testFactory {

    rootProject {
      IntegrationTestProjects.codeGeneratorProject(rootProjectBuilder = this, libs = libs)
    }
  }
}

class AssistedInjectionTest : FactoryGenTest() {

  @TestFactory
  fun `assisted injection code is generated`() = params.withFactoryGen { _, factoryGen ->

    rootProject {

      buildFile {

        plugins {
          kotlin("jvm")
          id("com.squareup.anvil")
          if (factoryGen.addKapt) {
            kotlin("kapt")
          }
        }

        anvil {
          generateDaggerFactories.set(factoryGen.genFactories)
        }

        dependenciesBlock(libs, factoryGen)
      }

      dir("src/main/java") {
        kotlinFile(
          "com/squareup/test/AppComponent.kt",
          """
            package com.squareup.test

            import com.squareup.anvil.annotations.MergeComponent
            import dagger.Module
            import dagger.Provides
            import dagger.assisted.AssistedFactory
            
            @MergeComponent(
              scope = AssistedScope::class,
              modules = [DaggerModule::class],
            )
            interface AppComponent {
              fun serviceFactory(): AssistedService.Factory
              fun serviceFactory2(): Factory2
            }
            
            @AssistedFactory
            interface Factory2 {
              fun create(string: String): AssistedServiceImpl
            }
            """,
        )
      }
    }

    shouldSucceed("jar")

    val classGraph = rootProject.classGraphResult()

    val appComponent = classGraph.appComponent()
    val daggerAppComponent = classGraph.daggerAppComponent()

    val moduleClass = classGraph.allModuleClasses()
      .shouldBeSingleton().single()

    moduleClass.allProvidesMethods().names() shouldBe listOf("provideBound")

    classGraph.allDaggerFactoryClasses()
      .shouldBeSingleton().single()
      .asClue { factory ->

        factory.packageName shouldBe "com.squareup.test"

        factory.name shouldBe "${moduleClass.name}_ProvideBoundFactory"

        factory.typeDescriptor.superinterfaceSignatures
          .map { it.fullyQualifiedClassName } shouldBe listOf("dagger.internal.Factory")

        factory.getMethodInfo("provideBound")
          .single()
          .typeSignatureOrTypeDescriptor.resultType.toString() shouldBe "com.squareup.test.Bound"
      }
  }
}
