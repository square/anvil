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

// The delegate build is only necessary for convenience, when this build is the root.
// If this build is being included elsewhere, there"s no need for it. If the root build is actually
// the delegate build, then including it here would create a circular dependency.
if (gradle.parent == null) {
  includeBuild("build-logic/delegate")
}
