package com.squareup.anvil.conventions

import com.rickbusarow.kgx.dependsOn
import com.rickbusarow.kgx.pluginId
import com.squareup.anvil.conventions.utils.isInDelegateBuild
import com.squareup.anvil.conventions.utils.isInMainAnvilBuild
import com.squareup.anvil.conventions.utils.libs
import org.gradle.api.Project
import org.gradle.api.tasks.Delete
import org.gradle.plugins.ide.idea.model.IdeaModel

open class LibraryPlugin : BasePlugin() {
  override fun Project.jvmTargetInt(): Int = libs.versions.jvm.target.library.get().toInt()

  override fun beforeApply(target: Project) {
    target.plugins.apply(target.libs.plugins.kotlin.jvm.pluginId)

    target.plugins.apply("idea")
  }

  override fun afterApply(target: Project) {

    configureBuildDirectory(target)

    if (target.isInMainAnvilBuild()) {
      configureDeleteBuildDirTask(target)
    }
  }

  private fun configureBuildDirectory(target: Project) {
    val anvilBuildDir = target.file("build-anvil")
    val delegateDir = target.file("build-delegate")

    // Make both the `build-anvil` and `build-delegate` directories show
    // up as build directories within the IDE.
    target.extensions.configure(IdeaModel::class.java) { idea ->
      idea.module { module ->
        module.excludeDirs.addAll(listOf(delegateDir, anvilBuildDir))
      }
    }

    if (target.isInDelegateBuild()) {
      target.layout.buildDirectory.set(delegateDir)
    } else {
      target.layout.buildDirectory.set(anvilBuildDir)
    }
  }

  private fun configureDeleteBuildDirTask(target: Project) {

    val deleteTask = target.tasks
      .register("deleteOldBuildDirs", Delete::class.java) { task ->
        task.delete(target.file("build"))
        task.delete(target.file("build-composite-wrapper"))
      }

    target.rootProject.tasks
      .named("prepareKotlinBuildScriptModel")
      .dependsOn(deleteTask)
  }
}
