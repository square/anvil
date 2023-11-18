package com.squareup.anvil.conventions

import com.squareup.anvil.benchmark.BenchmarkPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project

open class RootPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    target.plugins.apply("base")
    target.plugins.apply(BenchmarkPlugin::class.java)
    target.plugins.apply(KtlintConventionPlugin::class.java)

    if (target.isInMainAnvilBuild() || target.isInDelegateBuild()) {

      registerPublishStubTask(target)
    }
  }

  private fun registerPublishStubTask(target: Project) {
    // This task is registered in all projects in order to simplify publishing before the
    // `:gradle-plugin` integration tests.
    // Since the plugin doesn't actually directly depend upon the other projects, it doesn't force the
    // other projects to be configured.  This task is a simple hook to force that configuration in the
    // integration test workflow.
    target.tasks.register("publishToBuildM2") {
      it.group = "publishing"
      it.description = "Publishes all publications to the local Maven repository."
    }
  }
}
