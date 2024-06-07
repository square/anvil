package com.squareup.anvil.conventions

import com.squareup.anvil.conventions.utils.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.KtlintPlugin

open class KtlintConventionPlugin : Plugin<Project> {

  override fun apply(target: Project) {
    target.plugins.apply(KtlintPlugin::class.java)

    target.extensions.configure(KtlintExtension::class.java) { ktlint ->
      ktlint.version.set(target.libs.versions.ktlint)
      val pathsToExclude = listOf(
        "build/generated",
        "root-build/generated",
        "included-build/generated",
      )
      ktlint.filter {
        it.exclude {
          pathsToExclude.any { excludePath ->
            it.file.path.contains(excludePath)
          }
        }
      }
      ktlint.verbose.set(true)
    }
  }
}
