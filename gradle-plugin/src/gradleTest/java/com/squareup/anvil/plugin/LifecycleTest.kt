package com.squareup.anvil.plugin

import com.rickbusarow.kase.gradle.GradleKotlinTestVersions
import com.rickbusarow.kase.gradle.dsl.BuildFileSpec
import com.rickbusarow.kase.gradle.dsl.buildFile
import com.rickbusarow.kase.gradle.rootProject
import com.rickbusarow.kase.stdlib.div
import com.rickbusarow.kase.wrap
import com.squareup.anvil.plugin.buildProperties.kotlinVersion
import com.squareup.anvil.plugin.testing.AnvilGradleTestEnvironment
import com.squareup.anvil.plugin.testing.BaseGradleTest
import com.squareup.anvil.plugin.testing.anvil
import com.squareup.anvil.plugin.testing.classGraphResult
import io.kotest.matchers.file.shouldExist
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldInclude
import io.kotest.matchers.string.shouldNotInclude
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.TestFactory
import java.util.stream.Stream

class LifecycleTest : BaseGradleTest() {

  @TestFactory
  fun `tasks are compatible with configuration caching when targeting kotlin-jvm`() =
    params
      .distinctBy { it.gradleVersion }
      .withKspToggle { _, useKsp ->

        rootProject {
          buildFile {

            pluginsBlock(useKsp)
            anvilBlock(useKsp)
            dependencies {
              api(libs.dagger2.annotations)
              compileOnly(libs.inject)
            }
          }

          gradlePropertiesFile(
            """
            com.squareup.anvil.trackSourceFiles=true
            """.trimIndent(),
          )

          dir("src/main/java") {
            kotlinFile(
              "com/squareup/test/AppComponent.kt",
              """
              package com.squareup.test
    
              import com.squareup.anvil.annotations.MergeComponent
              import javax.inject.Singleton
    
              @MergeComponent(Any::class)
              interface AppComponent
              """.trimIndent(),
            )
            kotlinFile(
              "com/squareup/test/BoundClass.kt",
              """
              package com.squareup.test

              import com.squareup.anvil.annotations.ContributesBinding
              import javax.inject.Inject

              interface SomeInterface

              @ContributesBinding(Any::class)
              class BoundClass @Inject constructor() : SomeInterface
              """.trimIndent(),
            )
          }
        }

        val calculatingMessage =
          "Calculating task graph as no cached configuration is available for tasks: compileKotlin"

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
  fun `Kotlin tasks are not configured eagerly`() = params.asContainers { versions ->

    // tests for https://github.com/square/anvil/issues/1053

    listOf(true, false).asTests(
      testEnvironmentFactory = AnvilGradleTestEnvironment.Factory().wrap(versions),
      testName = { "with kapt: $it" },
    ) { useKapt ->

      val logPrefix = "configuring task"

      rootProject {

        buildFile {

          pluginsBlock(useKsp = false, addKapt = useKapt)

          anvilBlock(useKsp = false)

          dependencies {
            api(libs.dagger2.annotations)
            compileOnly(libs.inject)
            if (useKapt) {
              kapt(libs.dagger2.compiler)
            }
          }

          raw(
            """
            tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java).configureEach {
              println("$logPrefix: ${'$'}name")
            }
            """.trimIndent(),
          )
        }

        dir("src/main/java") {
          injectClass()
        }
      }

      shouldSucceed("help") {
        output.lines()
          .filter { logPrefix in it } shouldBe emptyList()
      }

      // Ensure that the task would fail by forcing configuration
      shouldSucceed("compileKotlin", "--dry-run") {

        val expected = buildList {
          add("configuring task: compileKotlin")
          if (useKapt) {
            add("configuring task: kaptGenerateStubsKotlin")
          }
        }

        output.lines().filter { logPrefix in it } shouldBe expected
      }
    }
  }

  @TestFactory
  fun `compileKotlin is up to date when no changes are made`() = params.withKspToggle { _, useKsp ->

    rootProject {

      buildFile {

        pluginsBlock(useKsp)

        anvilBlock(useKsp)

        dependencies {
          api(libs.dagger2.annotations)
          compileOnly(libs.inject)
        }
      }

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
  fun `compileKotlin is FROM-CACHE after the project build directory is deleted`() =
    params.withKspToggle { _, useKsp ->

      rootProject {

        buildFile {
          pluginsBlock(useKsp)

          anvilBlock(useKsp)

          dependencies {
            compileOnly(libs.inject)
            api(libs.dagger2.annotations)
          }
        }

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

      rootProject.generatedDir(useKsp).injectClassFactory.shouldExist()

      workingDir.resolve("build").deleteRecursivelyOrFail()

      // `compileJava` depends upon `compileKotlin`.
      // Compile Java just to ensure that everything that needed to be restored was restored.
      shouldSucceed("jar", withHermeticTestKit = true) {
        task(":compileKotlin")?.outcome shouldBe TaskOutcome.FROM_CACHE
      }

      rootProject.classGraphResult() shouldContainClass "com.squareup.test.InjectClass_Factory"

      rootProject.generatedDir(useKsp).injectClassFactory.shouldExist()
    }

  @TestFactory
  fun `build cache works across different project directories`() = params
    // This test exercises enough of the compiler that it runs into version compatibility issues.
    .filter { it.kotlinVersion == kotlinVersion }
    .withKspToggle { _, useKsp ->

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
              kotlin("jvm", apply = false)
              id("com.squareup.anvil", apply = false)
              id("com.google.devtools.ksp", apply = false)
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
              pluginsBlock(useKsp)

              anvilBlock(useKsp)

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
              pluginsBlock(useKsp)
              anvilBlock(useKsp)
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
              pluginsBlock(useKsp = useKsp, addKapt = !useKsp)

              dependencies {
                api(project(":lib-1"))
                api(project(":lib-2"))
                api(libs.dagger2.annotations)
                if (useKsp) {
                  ksp(libs.dagger2.compiler)
                } else {
                  kapt(libs.dagger2.compiler)
                }
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

  private fun BuildFileSpec.anvilBlock(useKsp: Boolean) {
    anvil {
      generateDaggerFactories.set(true)

      if (useKsp) {
        useKsp(
          contributesAndFactoryGeneration = true,
          componentMerging = true,
        )
      }
    }
  }

  private fun BuildFileSpec.pluginsBlock(useKsp: Boolean, addKapt: Boolean = false) {
    plugins {
      kotlin("jvm")
      id("com.squareup.anvil")
      if (useKsp) {
        id("com.google.devtools.ksp")
      }
      if (addKapt) {
        kotlin("kapt")
      }
    }
  }

  private inline fun <K : GradleKotlinTestVersions> List<K>.withKspToggle(
    crossinline testAction: suspend AnvilGradleTestEnvironment.(
      versions: K,
      useKsp: Boolean,
    ) -> Unit,
  ): Stream<out DynamicNode> = asContainers { versions ->
    listOf(false)
      .asTests(
        testEnvironmentFactory = AnvilGradleTestEnvironment.Factory().wrap(versions),
        testName = { _ -> "Embedded" },
        testAction = { useKsp -> testAction(versions, useKsp) },
      )
  }
}
