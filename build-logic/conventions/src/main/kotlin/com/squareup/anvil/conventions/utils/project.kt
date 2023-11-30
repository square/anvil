package com.squareup.anvil.conventions.utils

import com.rickbusarow.kgx.javaExtension
import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.tasks.SourceSet

internal fun Project.isInMainAnvilBuild() = rootProject.name == "anvil"
internal fun Project.isInDelegateBuild() = rootProject.name == "delegate"

internal val Project.gradlePublishingExtension: PublishingExtension
  get() = extensions.getByType(PublishingExtension::class.java)

internal val Project.libs: LibrariesForLibs
  get() = extensions.getByType(LibrariesForLibs::class.java)

internal fun Project.javaSourceSet(name: String): SourceSet {
  return javaExtension.sourceSets.getByName(name)
}
