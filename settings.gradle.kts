pluginManagement {
  repositories {
    google()
    gradlePluginPortal()
    mavenCentral()
  }

  includeBuild("build-logic/conventions")
  includeBuild("build-logic/delegate")
  includeBuild("build-logic/settings")
  includeBuild("lib")
}

plugins {
  id("com.squareup.anvil.gradle-settings")
}

rootProject.name = "anvil"
