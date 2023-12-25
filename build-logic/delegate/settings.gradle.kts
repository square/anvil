pluginManagement {
  repositories {
    google()
    gradlePluginPortal()
    mavenCentral()
  }

  includeBuild("../settings")
  includeBuild("../conventions")

  /*
   * Only include the root build here if this build is being included.  If this build is the root,
   * then the root build will be included below (not as a plugin build).
   *
   * When this build is included by the root build, the graph winds up as:
   *
   *                                              ┌──────────────────────────┐
   *                                              │       anvil project      │
   *                                              │      (root directory)    │
   *                                              │    ┌─────────────────┐   │
   *                       ┌──────────────────────┼────│ : (actual root) │   │
   *                       ▼                      │    └─────────────────┘   │
   *  ┌────────────────────────────────────────┐  │                          │
   *  │               :delegate                │  │                          │
   *  │          build-logic/delegate          │  │                          │
   *  │ ┌────────────┐   ┌───────────────────┐ │  │                          │
   *  │ │  :sample   │   │:integration-tests │ │  │                          │
   *  │ └────────────┘   └───────────────────┘ │  │                          │
   *  └────────────────────────────────────────┘  │   ┌───────────────────┐  │
   *                   │  │   └───────────────────┼──▶│ :anvil (included) │  │
   *                   │  │                       │   └───────────────────┘  │
   *                   │  └─────────────┐         └──────────────────────────┘
   *                   │                │   ┌──────────────────┘   │
   *                   │                ▼   ▼                      │
   *                   │       ┌───────────────────────────┐       │
   *                   │       │       :conventions        │       │
   *                   │       │  build-logic/conventions  │       │
   *                   │       └───────────────────────────┘       │
   *                   │    ┌───────────────┘                      │
   *                   │    │    ┌─────────────────────────────────┘
   *                   ▼    ▼    ▼
   *           ┌───────────────────────────┐
   *           │         :settings         │
   *           │   build-logic/settings    │
   *           └───────────────────────────┘
   */
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

/*
 * If this build is the root, then we include the root build here in order to get the gradle plugin.
 *
 * We include it here instead of in the pluginManagement block above because both of these builds
 * include the conventions build in `pluginManagement`, and that causes a race condition since
 * composite builds aren't thread-safe. Moving the root build down here ensures that they're
 * evaluated in order.
 *
 * When this build is the root build, the graph winds up as:
 *
 *  ┌────────────────────────────────────────┐
 *  │               :delegate                │
 *  │          build-logic/delegate          │
 *  │ ┌────────────┐   ┌───────────────────┐ │
 *  │ │  :sample   │   │:integration-tests │ │
 *  │ └────────────┘   └───────────────────┘ │
 *  └────────────────────────────────────────┘      ┌───────────────────┐
 *                   │  │   └──────────────────────▶│      :anvil       │
 *                   │  │                           │ (root directory)  │
 *                   │  └─────────────┐             └───────────────────┘
 *                   │                │   ┌──────────────────┘   │
 *                   │                ▼   ▼                      │
 *                   │       ┌───────────────────────────┐       │
 *                   │       │       :conventions        │       │
 *                   │       │  build-logic/conventions  │       │
 *                   │       └───────────────────────────┘       │
 *                   │    ┌───────────────┘                      │
 *                   │    │    ┌─────────────────────────────────┘
 *                   ▼    ▼    ▼
 *           ┌───────────────────────────┐
 *           │         :settings         │
 *           │   build-logic/settings    │
 *           └───────────────────────────┘
 */
if (gradle.parent == null) {
  includeBuild("../..")
}
