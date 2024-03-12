package com.squareup.anvil.plugin.testing

import com.rickbusarow.kase.KaseMatrix
import com.rickbusarow.kase.gradle.AgpDependencyVersion
import com.rickbusarow.kase.gradle.DaggerDependencyVersion
import com.rickbusarow.kase.gradle.GradleDependencyVersion
import com.rickbusarow.kase.gradle.HasKotlinDependencyVersion
import com.rickbusarow.kase.gradle.KotlinDependencyVersion
import com.rickbusarow.kase.gradle.KspDependencyVersion
import com.squareup.anvil.plugin.buildProperties.daggerVersion
import com.squareup.anvil.plugin.buildProperties.gradleVersion
import com.squareup.anvil.plugin.buildProperties.kotlinVersion

// TODO (rbusarow) move this to build-logic and sync it with the version catalog and `ci.yml`.
class AnvilVersionMatrix(
  agp: List<AgpDependencyVersion> = agpList,
  kotlin: List<KotlinDependencyVersion> = kotlinList,
  gradle: List<GradleDependencyVersion> = gradleList,
  dagger: List<DaggerDependencyVersion> = daggerList,
) : KaseMatrix by KaseMatrix(agp + kotlin + gradle + dagger) {
  private companion object {
    val agpList = setOf(
      "7.3.1",
      "7.4.2",
      // TODO (rbusarow) enable later AGP versions once we're building with JDK 17
      // "8.0.2", "8.1.1", "8.2.0",
    ).map(::AgpDependencyVersion)
    val kotlinList = setOf(kotlinVersion).map(::KotlinDependencyVersion)
    val gradleList = setOf(gradleVersion).map(::GradleDependencyVersion)
    val daggerList = setOf(daggerVersion).map(::DaggerDependencyVersion)
  }
}

/**
 * Returns the latest KSP version that's compatible with the receiver Kotlin version
 */
val HasKotlinDependencyVersion.kspDependencyVersion: KspDependencyVersion
  get() {
    val kspPart = when (kotlinVersion) {
      in ("1.9.0"..<"1.9.10") -> "1.0.11"
      in ("1.9.10"..<"1.9.20") -> "1.0.13"
      else -> "1.0.17"
    }
    return KspDependencyVersion("$kotlinVersion-$kspPart")
  }
