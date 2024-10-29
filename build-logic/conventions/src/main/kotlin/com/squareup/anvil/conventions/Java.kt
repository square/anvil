package com.squareup.anvil.conventions

import com.rickbusarow.kgx.javaExtension
import com.squareup.anvil.conventions.utils.libs
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion

internal fun Project.configureJavaCompile() {
  val jvmTargetInt = libs.versions.jvm.target.minimal.get().toInt()
  plugins.withId("java-base") {
    javaExtension.toolchain {
      it.languageVersion.set(JavaLanguageVersion.of(libs.versions.jvm.toolchain.get()))
    }
    javaExtension.targetCompatibility = JavaVersion.toVersion(jvmTargetInt)
    tasks.withType(JavaCompile::class.java).configureEach { task ->
      task.options.release.set(jvmTargetInt)
    }
  }
}
