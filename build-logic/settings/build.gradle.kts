import com.rickbusarow.kgx.fromInt
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

buildscript {
  dependencies {
    classpath(libs.kgx)
  }
}

plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.ktlint)
  id("java-gradle-plugin")
}

kotlin {
  jvmToolchain(libs.versions.jvm.toolchain.get().toInt())
  compilerOptions {
    jvmTarget.set(JvmTarget.fromInt(libs.versions.jvm.target.minimal.get().toInt()))
  }
}
tasks.withType(JavaCompile::class.java).configureEach {
  options.release.set(libs.versions.jvm.target.minimal.get().toInt())
}

ktlint {
  version = libs.versions.ktlint.get()
}

gradlePlugin {
  plugins {
    register("settingsPlugin") {
      id = "com.squareup.anvil.gradle-settings"
      implementationClass = "com.squareup.anvil.builds.settings.SettingsPlugin"
    }
  }
}
