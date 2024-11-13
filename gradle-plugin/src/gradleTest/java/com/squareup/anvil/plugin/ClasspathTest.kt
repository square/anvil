package com.squareup.anvil.plugin

import com.rickbusarow.kase.Kase4
import com.rickbusarow.kase.gradle.GradleKotlinAgpTestVersions
import com.rickbusarow.kase.gradle.GradleKotlinTestVersions
import com.rickbusarow.kase.gradle.versions
import com.rickbusarow.kase.kase
import com.squareup.anvil.plugin.buildProperties.anvilVersion
import com.squareup.anvil.plugin.testing.BaseGradleTest
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.TestFactory

internal class BuildType<T : GradleKotlinTestVersions>(
  override val displayName: String,
  val runtimeName: String,
  val compileName: String,
  val params: List<T>,
  val setup: (GradleKotlinTestVersions) -> String,
) : Kase4<String, String, List<T>, (GradleKotlinTestVersions) -> String> by kase(
  a1 = runtimeName,
  a2 = compileName,
  a4 = setup,
  a3 = params,
)

internal class ClasspathTest : BaseGradleTest() {

  val kotlinJvm = BuildType(
    displayName = "kotlin-jvm",
    runtimeName = "runtimeClasspath",
    compileName = "compileClasspath",
    params = kaseMatrix.versions(GradleKotlinTestVersions),
  ) { versions ->

    """
    plugins {
      kotlin("jvm") version "${versions.kotlinVersion}"
      id("com.squareup.anvil") version "$anvilVersion"
    }
    """.trimIndent()
  }

  val androidApp = BuildType(
    displayName = "android-app",
    runtimeName = "debugRuntimeClasspath",
    compileName = "debugCompileClasspath",
    params = kaseMatrix.versions(GradleKotlinAgpTestVersions),
  ) { versions ->
    versions as GradleKotlinAgpTestVersions
    """
    plugins {
      id("com.android.application") version "${versions.agpVersion}"
      kotlin("android") version "${versions.kotlinVersion}"
      id("com.squareup.anvil") version "$anvilVersion"
    }
    
    ${androidBlockString()}
    """.trimIndent()
  }
  val androidLibrary = BuildType(
    displayName = "android-library",
    runtimeName = "debugRuntimeClasspath",
    compileName = "debugCompileClasspath",
    params = kaseMatrix.versions(GradleKotlinAgpTestVersions),
  ) { versions ->
    versions as GradleKotlinAgpTestVersions
    """
    plugins {
      id("com.android.library") version "${versions.agpVersion}"
      kotlin("android") version "${versions.kotlinVersion}"
      id("com.squareup.anvil") version "$anvilVersion"
    }
    
    ${androidBlockString()}
    """.trimIndent()
  }

  /**
   * Why can't we just use dependency-guard to check the classpath?
   *
   * Dependency-Guard can check the `runtimeClasspath` and `compileClasspath` configurations
   * of the `gradle-plugin` project. That tells us what Anvil will contribute to a target project's
   * `classpath` configuration (the one modified in the `buildScript` block).
   *
   * For this test, we're testing what dependencies Anvil adds to the target project's own
   * `runtimeClasspath` and `compileClasspath` configurations.  Specifically, it adds the Anvil
   * annotations dependency -- but is it added correctly as a `compileOnly` dependency,
   * or is it being added as `implementation` or something else?
   */
  @TestFactory
  fun `annotations are only included in the compile classpath`() = listOf(
    kotlinJvm,
    androidApp,
    androidLibrary,
  ).asContainers { buildType ->

    val (runtimeName, compileName, params, setup) = buildType

    params.asTests { versions ->

      rootProject {

        buildFile(
          setup(versions) + """
    
          fun printDependencies(configurationName: String) {
            println(
              configurations.getByName(configurationName).incoming.dependencies
                .map { "${'$'}{it.group}:${'$'}{it.name}" }
                .sorted()
                .joinToString("\n")
            )
          }
  
          tasks.register("printRuntimeClasspath") {
            doLast { printDependencies("$runtimeName") }
          }
  
          tasks.register("printCompileClasspath") {
            doLast { printDependencies("$compileName") }
          }
          """.trimIndent(),
        )
      }

      shouldSucceed("printCompileClasspath", "printRuntimeClasspath") {
        val taskOutputs = output.split("> Task ", "\n\n")
          .associate {
            val lines = it.trim().lines()
            lines.first() to lines.drop(1).joinToString("\n")
              .replace(
                // This warning is generated when accessing `runtimeClasspath`
                // in projects using an old version of AGP.
                regex = """(?:\[Incubating] )?Problems report is available at.+""".toRegex(),
                replacement = "",
              )
              .trim()
          }

        val stdlib = when {
          versions.kotlinVersion < "1.9.20" -> "org.jetbrains.kotlin:kotlin-stdlib-jdk8"
          else -> "org.jetbrains.kotlin:kotlin-stdlib"
        }

        taskOutputs[":printCompileClasspath"] shouldBe """
          com.squareup.anvil:annotations
          $stdlib
        """.trimIndent()

        taskOutputs[":printRuntimeClasspath"] shouldBe stdlib
      }
    }
  }
}
