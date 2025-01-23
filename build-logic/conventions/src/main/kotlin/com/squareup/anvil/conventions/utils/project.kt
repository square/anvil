package com.squareup.anvil.conventions.utils

import com.rickbusarow.kgx.isPartOfRootBuild
import com.rickbusarow.kgx.javaExtension
import com.rickbusarow.kgx.parents
import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.plugin.devel.GradlePluginDevelopmentExtension

/**
 * Returns true if this project is included in the root `settings.gradle.kts` file,
 * using the syntax `include(":this-project")`.
 *
 * Note that due to the current composite build structure,
 * these projects can have two different build roots:
 *
 * ### ':' (ex: `:gradle-plugin`)
 * - the "true" root build, which is the root of the composite build
 * - All build/publishing/CI/etc. tasks happen in this build
 *
 * ### ':anvil' (ex: `:anvil:gradle-plugin`)
 * - not really the root build.
 * - This build is included by the `:delegate` build in order to consume the gradle plugin.
 *
 * @see isInAnvilRootBuild to check if this project is part of the true root build
 */
internal fun Project.isInAnvilBuild() = rootProject.name == "anvil"

/**
 * Returns true if this project is in the root 'anvil' build, and it is the true root of the build.
 *
 * @see isInAnvilBuild
 */
internal fun Project.isInAnvilRootBuild() = isInAnvilBuild() && gradle.parent == null

internal val Project.gradlePublishingExtension: PublishingExtension
  get() = extensions.getByType(PublishingExtension::class.java)

internal val Project.gradlePluginDevelopmentExtension: GradlePluginDevelopmentExtension
  get() = extensions.getByType(GradlePluginDevelopmentExtension::class.java)

internal val Project.libs: LibrariesForLibs
  get() = extensions.getByType(LibrariesForLibs::class.java)

internal fun Project.javaSourceSet(name: String): SourceSet {
  return javaExtension.sourceSets.getByName(name)
}

internal fun Project.addTasksToStartParameter(taskNames: Iterable<String>) {

  if (isPartOfRootBuild) {
    /* Root of composite build: We can just add the task name */
    gradle.startParameter.setTaskNames(
      gradle.startParameter.taskNames.toSet() + taskNames,
    )
  } else {
    /* This is an included build. Referencing the task path explicitly */
    val rootBuild = gradle.parents().last()
    val buildId = (project as ProjectInternal).identityPath
    val absoluteTaskPaths = taskNames.map { "$buildId:$it" }
    rootBuild.startParameter.setTaskNames(
      rootBuild.startParameter.taskNames.toSet() + absoluteTaskPaths,
    )
  }
}
