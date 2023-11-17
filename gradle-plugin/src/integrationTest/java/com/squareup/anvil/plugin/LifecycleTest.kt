package com.squareup.anvil.plugin

import com.rickbusarow.kase.files.buildDirectory
import com.rickbusarow.kase.stdlib.div
import io.kotest.matchers.shouldBe
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.TestFactory

class LifecycleTest : BaseAnvilGradleTest() {

  @TestFactory
  fun `compileKotlin is up to date when no changes are made`() = testFactory {
    rootBuild {
      plugins {
        kotlin("jvm", it.kotlinVersion)
        id("com.squareup.anvil", version = VERSION)
      }

      anvil {
        generateDaggerFactories.set(true)
      }

      dependencies {
        compileOnly(libs.dagger2.annotations)
      }
    }
    buildDirectory(root / "src/main/java") {
      kotlinFile(
        "com/squareup/test/InjectClass.kt",
        """
        package com.example
        
        import javax.inject.Inject
        
        class MyClass @Inject constructor()
        """.trimIndent()
      )
    }

    shouldSucceed("compileKotlin")
    shouldSucceed("compileKotlin") {
      task(":compileKotlin")?.outcome shouldBe TaskOutcome.UP_TO_DATE
    }
  }

  @TestFactory
  fun `compileKotlin is FROM-CACHE when no changes are made`() = testFactory {
    rootBuild {
      plugins {
        kotlin("jvm", it.kotlinVersion)
        id("com.squareup.anvil", version = VERSION)
      }

      anvil {
        generateDaggerFactories.set(true)
      }

      dependencies {
        compileOnly(libs.dagger2.annotations)
      }
    }
    buildDirectory(root / "src/main/java") {
      kotlinFile(
        "com/squareup/test/InjectClass.kt",
        """
        package com.example
        
        import javax.inject.Inject
        
        class MyClass @Inject constructor()
        """.trimIndent()
      )
    }
    root.resolve("gradle.properties").writeText(
      """
      org.gradle.caching=true
      """.trimIndent()
    )

    shouldSucceed("compileKotlin")

    root.resolve("build").deleteRecursively()

    shouldSucceed("compileKotlin") {
      task(":compileKotlin")?.outcome shouldBe TaskOutcome.FROM_CACHE
    }
  }
}
