package com.squareup.anvil.plugin

import com.rickbusarow.kase.gradle.AgpDependencyVersion
import com.rickbusarow.kase.gradle.DaggerDependencyVersion
import com.rickbusarow.kase.gradle.GradleDependencyVersion
import com.rickbusarow.kase.gradle.HasKotlinDependencyVersion
import com.rickbusarow.kase.gradle.KotlinDependencyVersion
import com.rickbusarow.kase.gradle.KspDependencyVersion
import com.rickbusarow.kase.gradle.VersionMatrix

// TODO (rbusarow) move this to build-logic and sync it with the version catalog and `ci.yml`.
class AnvilVersionMatrix(
  agp: List<AgpDependencyVersion> = agpList,
  kotlin: List<KotlinDependencyVersion> = kotlinList,
  gradle: List<GradleDependencyVersion> = gradleList,
  dagger: List<DaggerDependencyVersion> = daggerList,
) : VersionMatrix by VersionMatrix(agp + kotlin + gradle + dagger) {
  private companion object {
    val agpList = setOf("7.3.1", "7.4.2", "8.0.2", "8.1.1", "8.2.0").map(::AgpDependencyVersion)
    val kotlinList = setOf("1.8.21", "1.9.0", "1.9.10", "1.9.21").map(::KotlinDependencyVersion)
    val gradleList = setOf("8.5").map(::GradleDependencyVersion)
    val daggerList = setOf("2.46.1").map(::DaggerDependencyVersion)
  }
}

/**
 * Returns the latest KSP version that's compatible with the receiver Kotlin version
 */
val HasKotlinDependencyVersion.kspDependencyVersion: KspDependencyVersion
  get() {
    val kspPart = when (kotlinVersion) {
      in ("1.8.10"..<"1.8.20") -> "1.0.9"
      in ("1.8.20"..<"1.9.0") -> "1.0.11"
      in ("1.9.0"..<"1.9.10") -> "1.0.11"
      in ("1.9.10"..<"1.9.20") -> "1.0.13"
      else -> "1.0.15"
    }
    return KspDependencyVersion("$kotlinVersion-$kspPart")
  }
