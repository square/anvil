package com.squareup.anvil.plugin

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters.None
import kotlin.random.Random

/*
 * The key is important. Gradle uses different class loaders for different modules when the plugin
 * is applied through the new plugins { } syntax. If we'd use the same key, then the
 * IncrementalSignal class would be different for the same entry and Gradle would throw an error.
 *
 * By using a random key per class loader we avoid this issue. Since this service isn't shared
 * across modules and only within a module it's not causing any problems tasks only use the service
 * for their project.
 *
 * We could also create one service per module, but that seems wasteful. When the Gradle plugin is
 * added to classpath of the root project, then the key is always the same and all modules share
 * a single build service and a single cache.
 */
private val KEY = "incrementalSignal-" + Random.nextLong()

/** This signal is used to share state between the task above and Kotlin compile tasks. */
@Suppress("UnstableApiUsage")
abstract class IncrementalSignal : BuildService<None> {
  private val incremental = mutableMapOf<String, Boolean?>()

  @Synchronized fun setIncremental(
    projectPath: String,
    incremental: Boolean
  ) {
    this.incremental[projectPath] = incremental
  }

  @Synchronized fun isIncremental(projectPath: String): Boolean? = incremental[projectPath]

  companion object {
    fun registerIfAbsent(project: Project): Provider<IncrementalSignal> =
      project.gradle.sharedServices.registerIfAbsent(KEY, IncrementalSignal::class.java) { }
  }
}
