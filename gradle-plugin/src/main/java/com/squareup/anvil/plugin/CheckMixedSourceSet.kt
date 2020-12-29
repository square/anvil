package com.squareup.anvil.plugin

import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.provider.Property
import org.jetbrains.kotlin.gradle.internal.Kapt3GradleSubplugin
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * In a mixed Kotlin / Java source set the Kotlin compiler might crash with an error like this:
 *
 * java.lang.AssertionError: Duplicated JavaClassDescriptor a/b/c/Hello reported to IC.
 *
 * That's a known bug: https://youtrack.jetbrains.com/issue/KT-38576
 *
 * The workaround for now is to set the kotlin.incremental.usePreciseJavaTracking flag to false for
 * these module using this task.
 */
object CheckMixedSourceSet {

  fun preparePreciseJavaTrackingCheck(compileTask: KotlinCompile): Input {
    return Input(
      sources = getAndroidSourceDirs(compileTask)
        ?: getJvmSourceDirs(compileTask.project)
        ?: compileTask.project.files(),
      isKaptApplied = compileTask.project.isKaptApplied()
    )
  }

  fun disablePreciseJavaTrackingIfNeeded(
    compileTask: KotlinCompile,
    input: Input
  ) {
    val sourceFiles = input.sources
      .asSequence()
      .flatMap { it.walk() }
      .filter { it.isFile }
    val hasJavaFile = sourceFiles.any { it.extension == "java" }

    // If there is Java file, then disable precise Java tracking.
    //
    // If Kapt is enabled then it usually generates Java code making Kotlin compilation use a
    // mixed source set.
    if (hasJavaFile || input.isKaptApplied.get()) {
      compileTask.usePreciseJavaTracking = false
    }
  }

  private fun getAndroidSourceDirs(
    compileTask: KotlinCompile
  ): FileCollection? {
    val project = compileTask.project
    return if (project.isAndroidProject) {
      project.androidVariants()
        .findVariantForCompileTask(compileTask)
        .sourceSets
        .flatMap { it.javaDirectories }
        .let { project.files(it) }
    } else {
      null
    }
  }

  private fun getJvmSourceDirs(project: Project): FileCollection? {
    return if (project.isKotlinJvmProject) {
      project.convention.getPlugin(JavaPluginConvention::class.java)
        .sourceSets
        // Ignore "test", similar to androidVariants() we ignore unit tests.
        .single { it.name == "main" }
        .allJava
    } else {
      null
    }
  }

  private fun Project.isKaptApplied(): Property<Boolean> =
    objects
      .property(Boolean::class.java)
      .also {
        it.set(plugins.hasPlugin(Kapt3GradleSubplugin::class.java))
      }

  class Input(
    val sources: FileCollection,
    val isKaptApplied: Property<Boolean>
  )
}
