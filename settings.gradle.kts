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
include(":compiler-k2")
include(":compiler-utils")
include(":gradle-plugin")

// The delegate build is only necessary for convenience, when this build is the root.
// If this build is being included elsewhere, there's no need for it. If the root build is actually
// the delegate build, then including it here would create a circular dependency.
// TODO disabled temporarily for debugger support in tests. Otherwise, tests execute from the
//  delegate/included build but the debugger attaches to the version in the main build.
// if (gradle.parent == null) {
// includeBuild("build-logic/delegate")
// }
