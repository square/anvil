pluginManagement {
  repositories {
    google()
    gradlePluginPortal()
    mavenCentral()
  }
  includeBuild("../settings")
}

plugins {
  id("com.squareup.anvil.gradle-settings")
}

rootProject.name = "conventions"
