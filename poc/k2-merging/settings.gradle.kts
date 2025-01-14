pluginManagement {
  repositories {
    gradlePluginPortal()
    mavenCentral()
    google()
  }

  includeBuild("../../build-logic/settings")
  includeBuild("../../build-logic/conventions")
}

plugins {
  id("com.squareup.anvil.gradle-settings")
}

rootProject.name = "k2-merging"

include(
  "app",
  "library",
)

includeBuild("../..")
