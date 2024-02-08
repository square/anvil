import Settings_gradle.GradlePath
import org.gradle.api.internal.SettingsInternal
import org.gradle.util.Path

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
  id("com.gradle.enterprise") version "3.16.2"
}

gradleEnterprise {
  buildScan {
    termsOfServiceUrl = "https://gradle.com/terms-of-service"
    termsOfServiceAgree = "yes"
    publishAlways()
  }
}

rootProject.name = "anvil"

include(":annotations")
include(":annotations-optional")
include(":compiler")
include(":compiler-api")
include(":compiler-utils")
include(":gradle-plugin")

// The delegate build is only necessary for convenience, when this build is the root.
// If this build is being included elsewhere, there's no need for it. If the root build is actually
// the delegate build, then including it here would create a circular dependency.
if (gradle.parent == null) {
  if (providers.systemProperty("idea.active").orNull == "true") {
    if (gradle.startParameter.taskNames.none { it.endsWith(":test") }) {
      includeBuild("build-logic/delegate")
    }
  }
  // includeBuild("build-logic/delegate")
}

// if (target.providers.getSystemPropertyOrNull("idea.active") == "true") {
val startParameter = gradle.startParameter

val rootTestTasks = startParameter.taskNames.map {
  val path = Path.path(it)

  when {
    !path.isAbsolute -> it
    path.firstSegment != Path.path(":anvil") -> it
    path.lastSegment.path == ":test" -> path.removeFirstSegments(1).path
    path.lastSegment.path == ":gradleTest" -> path.removeFirstSegments(1).path
    else -> it
  }
}

val originalArgs = startParameter.taskRequests.flatMap { it.args }
val originalNames = startParameter.taskNames.toList()

if (startParameter.taskNames != rootTestTasks) {
  startParameter.setTaskNames(rootTestTasks)
}

val newNames = rootTestTasks.toList()
val newArgs = startParameter.taskRequests.flatMap { it.args }

println(
  """
  |%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%% thisBuild -- ${(settings as SettingsInternal)} -- gradle parent: ${gradle.parent}
  |     request type: ${startParameter.taskRequests.single().javaClass}
  |
  |   original names: $originalNames
  |        new names: $newNames
  |
  |    original args: $originalArgs
  |         new args: $newArgs
  |%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
  """.trimMargin(),
)
// }

/** For a path of `:a:b:c`, this would be `:a`. */
val GradlePath.firstSegment: GradlePath
  @Suppress("UnstableApiUsage")
  get() = this.takeFirstSegments(1)

/** For a path of `:a:b:c`, this would be `c`. */
val GradlePath.lastSegment: GradlePath
  get() = this.removeFirstSegments(this.segmentCount() - 1)
typealias GradlePath = org.gradle.util.Path
