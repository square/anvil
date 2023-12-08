pluginManagement {
  repositories {
    google()
    gradlePluginPortal()
    mavenCentral()
  }

  includeBuild("../settings")
  includeBuild("../conventions")

  // Only include the main build here if this build is being included.  If this build is the root,
  // then the main build will be included below (not as a plugin build).
  if (gradle.parent != null) {
    includeBuild("../..")
  }
}

plugins {
  id("com.squareup.anvil.gradle-settings")
}

rootProject.name = "delegate"

listOf(
  "integration-tests:code-generator",
  "integration-tests:code-generator-tests",
  "integration-tests:dagger-factories-only",
  "integration-tests:library",
  "integration-tests:mpp:android-module",
  "integration-tests:mpp:jvm-module",
  "integration-tests:tests",
  "sample:app",
  "sample:library",
  "sample:scopes",
).forEach { name ->
  include(":$name")
  project(":$name").projectDir = file("../../${name.replace(":", "/")}")
}

// If this build is the root, then we include the main build here in order to get the gradle plugin.
//
// We include it here instead of in the pluginManagement block above because both of these builds
// include the conventions build in `pluginManagement`, and that causes a race condition since
// composite builds aren't thread-safe.  Moving the main build down here ensures that they're
// evaluated in order.
if (gradle.parent == null) {
  includeBuild("../..")
}
