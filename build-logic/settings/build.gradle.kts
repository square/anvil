plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.ktlint)
  id("java-gradle-plugin")
}

kotlin {
  jvmToolchain(libs.versions.jvm.toolchain.get().toInt())
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
