package com.squareup.anvil.conventions

import com.rickbusarow.kgx.checkProjectIsRoot
import com.squareup.anvil.conventions.utils.namedOrNull
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.composite.internal.DefaultIncludedBuild
import org.gradle.composite.internal.DefaultIncludedBuild.IncludedBuildImpl
import org.gradle.internal.DefaultTaskExecutionRequest

class CompositePlugin : Plugin<Project> {

  override fun apply(target: Project) {

    target.checkProjectIsRoot()
    require(target.gradle.includedBuilds.isNotEmpty()) {
      "Only apply the 'composite' plugin to a root project with included builds.  " +
        "This project has no included builds, " +
        "so the plugin would just waste time searching the task graph."
    }

    target.gradle.projectsEvaluated { gradle ->

      val oldRequests = gradle.startParameter.taskRequests

      val newRequests = oldRequests.map { request ->

        val originalSplit = request.args
          .splitInclusive { !it.startsWith('-') }

        val taskPaths = originalSplit.mapTo(mutableSetOf()) { it.first() }

        val includedProjects = gradle.includedBuilds
          .asSequence()
          .filterIsInstance<IncludedBuildImpl>()
          .flatMap { impl ->

            val defaultIncludedBuild = impl.target as DefaultIncludedBuild

            defaultIncludedBuild.mutableModel.projectRegistry.allProjects
          }

        val newSplit = originalSplit.flatMap { taskWithArgs ->

          val taskName = taskWithArgs.first()

          if (taskName.startsWith(':')) {
            // qualified task names aren't propagated to included builds
            return@flatMap listOf(taskWithArgs)
          }

          val resolvedInRootBuild = target.tasks.namedOrNull(taskName)

          // Don't propagate help tasks
          if (taskName == "help") return@flatMap listOf(taskWithArgs)

          val inRoot = resolvedInRootBuild != null

          val included = includedProjects.mapNotNull { includedProject ->

            val includedPath = "${includedProject.identityPath}:$taskName"

            // Don't include tasks that are already in the task graph
            if (taskPaths.contains(includedPath)) return@mapNotNull null

            includedProject.tasks.namedOrNull(taskName) ?: return@mapNotNull null

            target.logger.quiet("The task $taskName will delegate to $includedPath")

            buildList<String> {
              add(includedPath)
              addAll(taskWithArgs.subList(1, taskWithArgs.size))
            }
          }

          buildList {
            if (inRoot) {
              add(taskWithArgs)
            }
            addAll(included)
          }
        }

        DefaultTaskExecutionRequest.of(newSplit.flatten(), request.projectPath, request.rootDir)
      }

      gradle.startParameter.setTaskRequests(newRequests)
    }
  }

  private inline fun <E> List<E>.splitInclusive(predicate: (E) -> Boolean): List<List<E>> {

    val toSplit = this@splitInclusive

    if (toSplit.isEmpty()) return emptyList()

    return toSplit.subList(1, toSplit.size)
      .fold(mutableListOf(mutableListOf(toSplit[0]))) { acc: MutableList<MutableList<E>>, e ->
        if (predicate(e)) {
          acc.add(mutableListOf(e))
        } else {
          acc.last().add(e)
        }
        acc
      }
  }
}
