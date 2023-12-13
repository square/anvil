package com.squareup.anvil.conventions.utils

import com.rickbusarow.kgx.getOrPut
import com.rickbusarow.kgx.isRootProject
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.plugin.extraProperties
import java.util.Locale

internal fun Project.hasTask(taskName: String): Boolean {

  if (tasks.names.contains(taskName)) return true

  // ex: [ "foo" : [ "foo", "Foo" ] ]
  val taskNamesLowercase = extraProperties
    .getOrPut<Map<String, List<String>>>("taskNamesLowercase") {
      tasks.names.groupBy { it.lowercase(Locale.US) }
    }

  val taskNameLowercase = taskName.lowercase(Locale.US)

  // All tasks that match the lowercase name. This will almost always be a singleton list or null,
  // but it can be multiple ambiguously named tasks.
  val lowercaseMatches = taskNamesLowercase[taskNameLowercase] ?: return false

  if (lowercaseMatches.size > 1) {
    val matchedPaths = lowercaseMatches.map {
      if (isRootProject()) ":$it" else "$path:$it"
    }
    logger.warn(
      "Cannot match a task with this name because of ambiguous capitalization: " +
        "'$taskName': $matchedPaths.",
    )
  }

  return lowercaseMatches.size == 1
}
