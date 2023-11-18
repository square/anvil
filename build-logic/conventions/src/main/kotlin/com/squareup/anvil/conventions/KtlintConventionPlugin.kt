package com.squareup.anvil.conventions

import com.rickbusarow.kgx.isRootProject
import com.rickbusarow.kgx.libsCatalog
import com.rickbusarow.kgx.version
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.KtlintPlugin

open class KtlintConventionPlugin : Plugin<Project> {

  override fun apply(target: Project) {
    target.plugins.apply(KtlintPlugin::class.java)

    target.extensions.configure(KtlintExtension::class.java) { ktlint ->
      ktlint.version.set(target.libsCatalog.version("ktlint"))
      ktlint.verbose.set(true)
    }

    if (target.isRootProject() && target.isInMainAnvilBuild()) {
      propagateToIncludedBuilds(target)
    }
  }

  /**
   * Make calls to `ktlintCheck` and `ktlintFormat` in the root project propagate to all included
   * builds.  These two invocations will be equivalent:
   *
   * ```sh
   * ./gradlew ktlintCheck
   * ./gradlew ktlintCheck :build-logic:conventions:ktlintCheck :build-logic:settings:ktlintCheck
   * ```
   */
  private fun propagateToIncludedBuilds(target: Project) {
    target.gradle.includedBuilds
      .filter { it.name != "delegate" }
      .forEach { build ->
        target.tasks.named("ktlintCheck") { task ->
          task.dependsOn(build.task(":ktlintCheck"))
        }
        target.tasks.named("ktlintFormat") { task ->
          task.dependsOn(build.task(":ktlintFormat"))
        }
      }
  }
}
