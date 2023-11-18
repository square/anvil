package com.squareup.anvil.conventions

import com.rickbusarow.kgx.libsCatalog
import com.rickbusarow.kgx.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.plugins.ide.idea.model.IdeaModel

open class LibraryPlugin : Plugin<Project> {
  override fun apply(target: Project) {

    target.plugins.apply(target.libsCatalog.pluginId("kotlin-jvm"))
    target.plugins.apply(KtlintConventionPlugin::class.java)

    when (target.path) {
      ":annotations",
      ":annotations-optional",
      ":compiler",
      ":compiler-api",
      ":compiler-utils",
      ":gradle-plugin",
      -> target.setBuildDir()
    }
  }

  private fun Project.setBuildDir() {

    if (file("build").exists()) {
      file("build").deleteRecursively()
    }
    val anvilBuildDir = file("build-anvil")
    val delegateDir = file("build-delegate")

    plugins.apply("idea")
    // Make both the `build-anvil` and `build-delegate` directories show
    // up as build directories within the IDE.
    extensions.configure(IdeaModel::class.java) { idea ->
      idea.module { module ->
        module.excludeDirs.addAll(listOf(delegateDir, anvilBuildDir))
      }
    }

    if (isInDelegateBuild()) {
      layout.buildDirectory.set(delegateDir)
    } else {
      layout.buildDirectory.set(anvilBuildDir)
    }
  }
}
