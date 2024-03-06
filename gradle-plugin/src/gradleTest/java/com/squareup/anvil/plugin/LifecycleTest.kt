package com.squareup.anvil.plugin

import com.rickbusarow.kase.gradle.dsl.buildFile
import com.rickbusarow.kase.gradle.rootProject
import com.rickbusarow.kase.stdlib.div
import com.squareup.anvil.plugin.buildProperties.kotlinVersion
import com.squareup.anvil.plugin.testing.BaseGradleTest
import com.squareup.anvil.plugin.testing.anvil
import io.kotest.matchers.file.shouldExist
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldInclude
import io.kotest.matchers.string.shouldNotInclude
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.TestFactory

class LifecycleTest : BaseGradleTest() {

  @TestFactory
  fun `tasks are compatible with configuration caching when targeting kotlin-jvm`() =
    params
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
        com.squareup.anvil.trackSourceFiles=true
        """.trimIndent(),
      )
    }

    shouldSucceed("compileJava", withHermeticTestKit = true)

    workingDir.resolve("build").deleteRecursively()

    // `compileJava` depends upon `compileKotlin`.
    // Compile Java just to ensure that everything that needed to be restored was restored.
    shouldSucceed("compileJava", withHermeticTestKit = true) {
      task(":compileKotlin")?.outcome shouldBe TaskOutcome.FROM_CACHE

      rootAnvilMainGenerated.injectClassFactory.shouldExist()
    }
  }

  @TestFactory
  fun `build cache works across different project directories`() = testFactory(
    // This test exercises enough of the compiler that it runs into version compatibility issues.
    params.filter { it.kotlinVersion == kotlinVersion },
  ) { versions ->

    // The testKit directory has the daemon and build cache.
    // We'll use the same testKit directory for both projects
    // to simulate having a shared remote build cache.
    val runner = gradleRunner
      .withTestKitDir(workingDir / "testKit")
      .withArguments("compileJava", "--stacktrace")

    val rootA = workingDir / "root-a"
    // The second project has an extra parent directory
    // so that `root-a` and `root-b` have different relative paths to the build cache.
    val rootB = workingDir / "dir/root-b"

    for (root in listOf(rootA, rootB)) {
      rootProject(path = root) {
        buildFile {
          plugins {
            kotlin("jvm", version = versions.kotlinVersion, apply = false)
            id("com.squareup.anvil", version = anvilVersion, apply = false)
          }
        }
        settingsFile(
          """
          ${rootProject.settingsFileAsFile.readText()}

          include("lib-1")
          include("lib-2")
          include("app")
          """.trimIndent(),
        )

        gradlePropertiesFile(
          """
          org.gradle.caching=true
          """.trimIndent(),
        )

        project("lib-1") {
          buildFile {
            plugins {
              kotlin("jvm")
              id("com.squareup.anvil")
            }
            anvil {
              generateDaggerFactories.set(true)
            }
            dependencies {
              api(libs.dagger2.annotations)
              compileOnly(libs.inject)
            }
          }

          dir("src/main/java") {
            injectClass("com.squareup.lib1")
          }
        }

        project("lib-2") {
          buildFile {
            plugins {
              kotlin("jvm")
              id("com.squareup.anvil")
            }
            anvil {
              generateDaggerFactories.set(true)
            }
            dependencies {
              api(libs.dagger2.annotations)
              api(project(":lib-1"))
              compileOnly(libs.inject)
            }
          }

          dir("src/main/java") {
            kotlinFile(
              "com/squareup/lib2/OtherClass.kt",
              """
              package com.squareup.lib2
              
              import com.squareup.lib1.InjectClass
              import javax.inject.Inject
              
              class OtherClass @Inject constructor(
                private val injectClass: InjectClass
              )
              """.trimIndent(),
            )
          }
        }

        project("app") {
          buildFile {
            plugins {
              kotlin("jvm")
              kotlin("kapt")
              id("com.squareup.anvil")
            }
            dependencies {
              api(project(":lib-1"))
              api(project(":lib-2"))
              api(libs.dagger2.annotations)
              kapt(libs.dagger2.compiler)
            }
          }

          dir("src/main/java") {
            kotlinFile(
              "com/squareup/lib2/InjectClass.kt",
              """
              package com.squareup.app
              
              import com.squareup.anvil.annotations.MergeComponent
              import com.squareup.lib1.InjectClass
              import javax.inject.Inject
              
              @MergeComponent(Unit::class) 
              interface AppComponent
              """.trimIndent(),
            )
          }
        }
      }
    }

    // Delete the normal auto-generated "root" Gradle files
    // since we created other projects to work with.
    rootProject.buildFileAsFile.delete()
    rootProject.settingsFileAsFile.delete()

    runner.withProjectDir(rootA).build().apply {
      task(":lib-1:compileKotlin")?.outcome shouldBe TaskOutcome.SUCCESS
      task(":lib-2:compileKotlin")?.outcome shouldBe TaskOutcome.SUCCESS
      task(":app:compileKotlin")?.outcome shouldBe TaskOutcome.SUCCESS
    }

    runner.withProjectDir(rootB).build().apply {
      task(":lib-1:compileKotlin")?.outcome shouldBe TaskOutcome.FROM_CACHE
      task(":lib-2:compileKotlin")?.outcome shouldBe TaskOutcome.FROM_CACHE
      task(":app:compileKotlin")?.outcome shouldBe TaskOutcome.FROM_CACHE
    }
  }
}
