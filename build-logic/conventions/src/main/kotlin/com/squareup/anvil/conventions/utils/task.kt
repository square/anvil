package com.squareup.anvil.conventions.utils

import com.rickbusarow.kgx.getOrPut
import org.gradle.api.NamedDomainObjectCollectionSchema.NamedDomainObjectSchema
import org.gradle.api.tasks.TaskCollection
import org.jetbrains.kotlin.gradle.plugin.extraProperties

/** @throws IllegalArgumentException if the task name is ambiguous when case is ignored */
internal fun TaskCollection<*>.namedOrNull(taskName: String): NamedDomainObjectSchema? {

  val namesLowercase = extraProperties.getOrPut("taskNamesLowercase") {
    collectionSchema.elements.groupBy { it.name.lowercase() }
  }

  val taskNameLowercase = taskName.lowercase()

  val matches = namesLowercase[taskNameLowercase] ?: return null

  val exactMatch = matches.singleOrNull { it.name == taskName }

  if (exactMatch != null) {
    return exactMatch
  }

  require(matches.size == 1) {
    "Task name '$taskName' is ambiguous.  " +
      "It matches multiple tasks: ${matches.map { it.name }}"
  }

  return matches.single()
}
