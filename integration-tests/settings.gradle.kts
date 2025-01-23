pluginManagement {
  repositories {
    gradlePluginPortal()
    mavenCentral()
    google()
  }

  includeBuild("../build-logic/settings")
  includeBuild("../build-logic/conventions")
}

plugins {
  id("com.squareup.anvil.gradle-settings")
}

rootProject.name = "integration-tests"

include(
  "code-generator",
  "code-generator-tests",
  "dagger-factories-only",
  "library",
  "mpp:android-module",
  "mpp:jvm-module",
  "tests",
)

/*
 * We include the root build here in order to get the Anvil gradle plugin.
 *
 * We include it here instead of in the pluginManagement block above because anvil, samples,
 * and integration-tests all include the conventions build in `pluginManagement`,
 * and that causes a race condition since composite builds aren't thread-safe.
 * Moving the root build down here ensures that they're evaluated in order.
 *
 * When this build is the root build, the graph winds up as:
 *
 *  ┌────────────────────────────────────┐
 *  │         :integration-tests         │
 *  │       anvil/integration-tests      │
 *  └────────────────────────────────────┘      ┌───────────────────┐
 *                   │  │   └──────────────────▶│      :anvil       │
 *                   │  │                       │ (root directory)  │
 *                   │  └─────────────┐         └───────────────────┘
 *                   │                │   ┌──────────────┘   │
 *                   │                ▼   ▼                  │
 *                   │       ┌───────────────────────────┐   │
 *                   │       │       :conventions        │   │
 *                   │       │  build-logic/conventions  │   │
 *                   │       └───────────────────────────┘   │
 *                   │    ┌───────────────┘                  │
 *                   │    │    ┌─────────────────────────────┘
 *                   ▼    ▼    ▼
 *           ┌───────────────────────────┐
 *           │         :settings         │
 *           │   build-logic/settings    │
 *           └───────────────────────────┘
 */
includeBuild("..")
