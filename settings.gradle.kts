pluginManagement {
  repositories {
    google()
    gradlePluginPortal()
    mavenCentral()
  }

  includeBuild("build-logic/conventions")
  includeBuild("build-logic/settings")
}

plugins {
  id("com.squareup.anvil.gradle-settings")
}

rootProject.name = "anvil"

include(":annotations")
include(":annotations-optional")
include(":compiler")
include(":compiler-api")
include(":compiler-utils")
include(":gradle-plugin")
