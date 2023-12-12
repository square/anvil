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

internal fun Project.isInMainAnvilBuild() = rootProject.name == "anvil"
internal fun Project.isInDelegateBuild() = rootProject.name == "delegate"

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
