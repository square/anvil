import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.ktlint)
  id("java-gradle-plugin")
}

kotlin {
  jvmToolchain(libs.versions.jvm.toolchain.get().toInt())
  compilerOptions {
    jvmTarget.set(JvmTarget.fromTarget(libs.versions.jvm.target.minimal.get()))
  }
}
java.targetCompatibility = JavaVersion.toVersion(libs.versions.jvm.target.minimal.get())

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
