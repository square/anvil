package com.squareup.anvil.conventions

import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension

internal fun Project.isInMainAnvilBuild() = rootProject.name == "anvil"

internal fun Project.isInDelegateBuild() = rootProject.name == "delegate"

internal val Project.gradlePublishingExtension: PublishingExtension
  get() = extensions.getByType(PublishingExtension::class.java)
