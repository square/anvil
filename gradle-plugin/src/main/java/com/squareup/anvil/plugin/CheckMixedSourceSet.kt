package com.squareup.anvil.plugin

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginConvention
import org.jetbrains.kotlin.gradle.internal.Kapt3GradleSubplugin
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.File

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
open class CheckMixedSourceSet(
  private val project: Project,
  private val compileTask: KotlinCompile
) {

  fun disablePreciseJavaTrackingIfNeeded() {
    val sourceFiles = when {
      project.isAndroidProject -> getSourceFilesAndroidProject()
      project.isKotlinJvmProject -> getSourceFilesJavaProject()
      // Consider Kotlin only project in the future.
      else -> throw GradleException("Only Android and JVM modules are supported for now.")
    }

    // If there is Java file, then disable precise Java tracking.
    val hasJavaFile = sourceFiles.any { it.extension == "java" }

    // If Kapt is enabled then it usually generates Java code making Kotlin compilation use a
    // mixed source set.
    val isKaptApplied = project.plugins.hasPlugin(Kapt3GradleSubplugin::class.java)

    if (hasJavaFile || isKaptApplied) {
      compileTask.usePreciseJavaTracking = false
    }
  }

  @OptIn(ExperimentalStdlibApi::class)
  private fun getSourceFilesAndroidProject(): Sequence<File> {
    return project.androidVariants()
        .findVariantForCompileTask(compileTask)
        .sourceSets
        .asSequence()
        .flatMap { it.javaDirectories.asSequence() }
        .flatMap { it.walk() }
        .filter { it.isFile }
  }

  private fun getSourceFilesJavaProject(): Sequence<File> {
    return project.convention.getPlugin(JavaPluginConvention::class.java)
        .sourceSets
        // Ignore "test", similar to androidVariants() we ignore unit tests.
        .single { it.name == "main" }
        .allJava
        .asSequence()
        .filter { it.isFile }
  }
}
