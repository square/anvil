package com.squareup.anvil.conventions.utils

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult

/**
 * Returns a sequence of all dependencies in this configuration, including transitive dependencies,
 * in a breadth-first traversal.  Dependencies are resolved lazily, after their dependent/consumer
 * dependency has been visited.
 *
 * In this context, "resolve" means that the dependency's metadata was inspected,
 * but no build tasks are executed and no binaries are downloaded.
 */
internal inline fun Configuration.allDependenciesSequence(
  crossinline onEnter: (ResolvedComponentResult) -> Boolean = { true },
): Sequence<ModuleVersionIdentifier> {

  return sequence {

    val visited = mutableSetOf<ResolvedComponentResult>()

    val root = incoming.resolutionResult.root
    val toDo = ArrayDeque<ResolvedComponentResult>(root.dependencies.size + 1)
    toDo.add(root)

    while (toDo.isNotEmpty()) {
      val next = toDo.removeFirst()
      if (visited.add(next) && onEnter(next)) {
        next.moduleVersion?.let { yield(it) }
        val deps = next.dependencies
          .filterIsInstance<ResolvedDependencyResult>()
          .map { it.selected }
        toDo.addAll(deps)
      }
    }
  }
}
