package com.squareup.anvil.plugin

import com.rickbusarow.kase.gradle.GradleDependencyVersion
import com.rickbusarow.kase.gradle.GradleKotlinTestVersions
import com.rickbusarow.kase.gradle.KotlinDependencyVersion
import com.squareup.anvil.plugin.testing.BaseGradleTest
import io.kotest.matchers.string.shouldInclude
import org.junit.jupiter.api.Test

class KotlinVersionCompatibilityCheckTest : BaseGradleTest() {

  fun lastKotlinPatch() = when (KotlinVersion.CURRENT.toString()) {
    in "1.9.0"..<"1.9.1" -> KotlinDependencyVersion("1.8.22")
    in "1.9.1"..<"1.9.2" -> KotlinDependencyVersion("1.9.0")
    in "1.9.2"..<"1.9.3" -> KotlinDependencyVersion("1.9.10")
    in "2.0.0"..<"2.0.1" -> KotlinDependencyVersion("1.9.23")
    else -> error("No \"last\" Kotlin version is defined yet for ${KotlinVersion.CURRENT}")
  }

  fun nextKotlinPatch() = when (KotlinVersion.CURRENT.toString()) {
    in "1.9.0"..<"1.9.1" -> KotlinDependencyVersion("1.9.10")
    in "1.9.1"..<"1.9.2" -> KotlinDependencyVersion("1.9.23")
    in "1.9.2"..<"1.9.3" -> KotlinDependencyVersion("2.0.0-RC1")
    else -> error("No \"last\" Kotlin version is defined yet for ${KotlinVersion.CURRENT}")
  }

  @Test
  fun `a too-low Kotlin version throws a warning`() = test(
    GradleKotlinTestVersions(
      gradleVersion = GradleDependencyVersion.current(),
      kotlinVersion = lastKotlinPatch(),
    ),
  ) {

    rootProject {
      dir("src/main/java") {
        injectClass()
      }
    }

    shouldSucceed("compileKotlin") {
      output shouldInclude "The Kotlin version used in the project is lower than the version Anvil was compiled with."
    }
  }
}
