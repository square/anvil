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
  id("com.gradle.develocity") version "3.17.5"
}

develocity {
  buildScan {
    uploadInBackground = true
    termsOfUseUrl = "https://gradle.com/help/legal-terms-of-use"
    termsOfUseAgree = "yes"
  }
}

rootProject.name = "anvil"

include(":annotations")
include(":annotations-optional")
include(":compiler")
include(":compiler-api")
// include(":compiler-k2")
include(":compiler-utils")
include(":gradle-plugin")
