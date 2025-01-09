package com.squareup.anvil.conventions

import com.rickbusarow.kgx.checkProjectIsRoot
import com.squareup.anvil.benchmark.BenchmarkPlugin
import com.squareup.anvil.conventions.utils.isInAnvilBuild
import com.squareup.anvil.conventions.utils.libs
import org.gradle.api.Project
import kotlin.io.path.createSymbolicLinkPointingTo
import kotlin.io.path.deleteIfExists
import kotlin.io.path.isSymbolicLink

open class RootPlugin : BasePlugin() {
  override fun Project.jvmTargetInt(): Int = libs.versions.jvm.target.minimal.get().toInt()
  override fun beforeApply(target: Project) {

    target.checkProjectIsRoot { "RootPlugin must only be applied to the root project" }

    if (target.isInAnvilBuild()) {
      target.plugins.apply(BenchmarkPlugin::class.java)
    } else {
      target.tasks.register("symlinkGradleFiles") { task ->

        val anvilDir = target.gradle.includedBuild("anvil").projectDir.toPath()

        val relativeFiles = listOf(
          "gradle.properties",
          "gradlew",
          "gradlew.bat",
          "gradle/wrapper/gradle-wrapper.properties",
          "gradle/wrapper/gradle-wrapper.jar",
        )

        task.inputs.files(relativeFiles.map { anvilDir.resolve(it) })
        task.outputs.files(relativeFiles.map { target.file(it) })

        task.doLast {

          relativeFiles.forEach { relative ->
            val symlink = target.file(relative).toPath()
            if (!symlink.isSymbolicLink()) {
              symlink.deleteIfExists()
              symlink.createSymbolicLinkPointingTo(anvilDir.resolve(relative))
            }
          }
        }
      }
    }

    target.plugins.apply("java-base")
  }

  override fun afterApply(target: Project) {

    target.logVersionInfo()

    target.registerPublishStubTask()
  }

  private fun Project.logVersionInfo() {
    val kotlinVersion = libs.versions.kotlin.get()
    val fullTestRun = libs.versions.config.fullTestRun.get()

    logger.info(
      "Versions: ${
        mapOf(
          "Kotlin" to kotlinVersion,
          "Gradle" to gradle.gradleVersion,
          "Full Test Run" to fullTestRun,
        )
      }",
    )
  }

  /**
   * This task is registered in all projects to simplify publishing before the `:gradle-plugin`
   * integration tests.
   *
   * Since the plugin doesn't directly depend upon the other projects, it doesn't force the other
   * projects to be configured. This task is a simple hook to force that configuration in the
   * integration test workflow.
   */
  private fun Project.registerPublishStubTask() {
    tasks.register("publishToBuildM2") {
      it.group = "publishing"
      it.description = "Publishes all publications to the local Maven repository."
    }
  }
}
