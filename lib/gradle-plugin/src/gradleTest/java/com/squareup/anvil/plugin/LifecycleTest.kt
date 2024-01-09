package com.squareup.anvil.plugin

import com.rickbusarow.kase.gradle.dsl.buildFile
import com.rickbusarow.kase.gradle.rootProject
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldInclude
import io.kotest.matchers.string.shouldNotInclude
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.TestFactory

class LifecycleTest : BaseGradleTest() {

  @TestFactory
  fun `tasks are compatible with configuration caching when targeting kotlin-jvm`() =
    kases
      .distinctBy { it.gradleVersion }
      .asTests {

        rootProject {
          buildFile {
            plugins {
              kotlin("jvm", it.kotlinVersion)
              id("com.squareup.anvil", anvilVersion)
            }
            dependencies {
              compileOnly(libs.inject)
            }
          }

          dir("src/main/java") {
            injectClass()
          }
        }

        val calculatingMessage = when {
          it.gradleVersion >= "8.5" -> "Calculating task graph as no cached configuration is available for tasks: compileKotlin"
          else -> "Calculating task graph as no configuration cache is available for tasks: compileKotlin"
        }

        val storingMessage = "Configuration cache entry stored."

        val reusingMessage = "Reusing configuration cache."

        shouldSucceed(
          "compileKotlin",
          "--configuration-cache",
          withHermeticTestKit = true,
        ) {
          output shouldInclude calculatingMessage
          output shouldInclude storingMessage
          output shouldNotInclude reusingMessage
        }

        shouldSucceed(
          "compileKotlin",
          "--configuration-cache",
          withHermeticTestKit = true,
        ) {
          output shouldNotInclude calculatingMessage
          output shouldNotInclude storingMessage
          output shouldInclude reusingMessage
        }
      }

  @TestFactory
  fun `compileKotlin is up to date when no changes are made`() = testFactory {

    rootProject {
      dir("src/main/java") {
        injectClass()
      }
    }

    shouldSucceed("compileKotlin")
    shouldSucceed("compileKotlin") {
      task(":compileKotlin")?.outcome shouldBe TaskOutcome.UP_TO_DATE
    }
  }

  @TestFactory
  fun `compileKotlin is FROM-CACHE after the project build directory is deleted`() = testFactory {
    rootProject {
      dir("src/main/java") {
        injectClass()
      }
      gradlePropertiesFile(
        """
        org.gradle.caching=true
        """.trimIndent(),
      )
    }

    shouldSucceed("compileKotlin")

    workingDir.resolve("build").deleteRecursively()

    shouldSucceed("compileKotlin") {
      task(":compileKotlin")?.outcome shouldBe TaskOutcome.FROM_CACHE
    }
  }
}
