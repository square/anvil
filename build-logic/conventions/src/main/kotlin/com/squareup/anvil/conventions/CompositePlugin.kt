package com.squareup.anvil.conventions

import com.rickbusarow.kgx.checkProjectIsRoot
import com.squareup.anvil.conventions.utils.hasTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.composite.internal.DefaultIncludedBuild
import org.gradle.composite.internal.DefaultIncludedBuild.IncludedBuildImpl
import org.gradle.internal.DefaultTaskExecutionRequest

/**
 * This plugin propagates unqualified task invocations to included builds. Conceptually, it
 * behaves as if the included builds were subprojects of the root build.
 *
 * For example, if the root project has an included build named `:included`,
 * and `:included` has a task named `:included:foo`, then the root project can invoke
 * `./gradlew foo` and the task `:included:foo` will be executed.
 */
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

        val originalSplit = request.args.splitTaskArgs()

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

          // Don't propagate help tasks
          if (taskName == "help") return@flatMap listOf(taskWithArgs)

          val included = includedProjects.mapNotNull { includedProject ->

            val includedPath = "${includedProject.identityPath}:$taskName"

            // Don't include tasks that are already in the task graph
            if (taskPaths.contains(includedPath)) return@mapNotNull null

            if (!includedProject.hasTask(taskName)) return@mapNotNull null

            target.logger.quiet("The task $taskName will delegate to $includedPath")

            buildList<String> {
              add(includedPath)
              addAll(taskWithArgs.subList(1, taskWithArgs.size))
            }
          }

          buildList {

            // Looks to see if any project in this build has a task with this name.
            val resolvedInRootBuild = target.allprojects.any { it.hasTask(taskName) }

            if (resolvedInRootBuild) {
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

  private fun List<String>.splitTaskArgs(): List<List<String>> {

    val toSplit = this@splitTaskArgs

    if (toSplit.isEmpty()) return emptyList()

    val args = toSplit.subList(1, toSplit.size)

    return args
      .fold(
        mutableListOf(mutableListOf(toSplit[0])),
      ) { acc: MutableList<MutableList<String>>, arg ->

        val lastArg = acc.last().last()

        when {
          arg.startsWith('-') -> acc.last().add(arg)
          // Matches 'foo' in `./gradlew test --tests foo`
          // Does not match 'foo' in `./gradlew test --tests=foo`
          lastArg.startsWith('-') && !lastArg.contains('=') -> acc.last().add(arg)
          else -> acc.add(mutableListOf(arg))
        }
        acc
      }
  }
}
