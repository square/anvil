package com.squareup.anvil.conventions

import com.rickbusarow.kgx.javaExtension
import com.rickbusarow.kgx.withAny
import com.squareup.anvil.conventions.utils.allDependenciesSequence
import com.squareup.anvil.conventions.utils.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8
import org.jetbrains.kotlin.gradle.internal.KaptGenerateStubsTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.Locale

abstract class BasePlugin : Plugin<Project> {

  open fun beforeApply(target: Project) = Unit
  open fun afterApply(target: Project) = Unit

  abstract fun Project.jvmTargetInt(): Int

  final override fun apply(target: Project) {

    beforeApply(target)

    target.plugins.apply("base")

    target.plugins.apply(KtlintConventionPlugin::class.java)

    configureGradleProperties(target)

    target.plugins.withAny(
      "org.jetbrains.kotlin.android",
      "org.jetbrains.kotlin.jvm",
      "org.jetbrains.kotlin.multiplatform",
    ) {
      configureKotlin(target)
    }
    configureTestLogging(target)

    configureJava(target)

    target.configurations.configureEach { config ->
      config.resolutionStrategy {
        it.force(target.libs.kotlin.metadata)
      }
    }

    afterApply(target)
  }

  private fun configureGradleProperties(target: Project) {
    target.version = target.property("VERSION_NAME") as String
    target.group = target.property("GROUP") as String
  }

  private fun configureKotlin(target: Project) {
    target.tasks.withType(KotlinCompile::class.java).configureEach { task ->
      task.compilerOptions {

        allWarningsAsErrors.set(target.libs.versions.config.warningsAsErrors.get().toBoolean())

        val hasIt = task.hasAnnotationsDependency()

        if (hasIt) {
          optIn.add("com.squareup.anvil.annotations.ExperimentalAnvilApi")
        }

        val fromInt = when (val targetInt = target.jvmTargetInt()) {
          8 -> JVM_1_8
          else -> JvmTarget.fromTarget("$targetInt")
        }
        jvmTarget.set(fromInt)
      }
    }
  }

  private fun KotlinCompile.hasAnnotationsDependency(): Boolean {
    return compileClasspathConfiguration()
      ?.allDependenciesSequence { resolved ->
        resolved.moduleVersion?.group == "com.squareup.anvil"
      }
      ?.any { moduleVersion ->
        moduleVersion.module.toString() == "com.squareup.anvil:annotations"
      } ?: false
  }

  private fun configureJava(target: Project) {
    target.plugins.withId("java") {
      target.javaExtension.toolchain {
        it.languageVersion.set(JavaLanguageVersion.of(target.libs.versions.jvm.toolchain.get()))
      }
      target.tasks.withType(JavaCompile::class.java).configureEach { task ->
        task.options.release.set(target.jvmTargetInt())
      }
    }
  }

  private fun configureTestLogging(target: Project) {
    target.tasks.withType(Test::class.java).configureEach { task ->

      task.maxParallelForks = Runtime.getRuntime().availableProcessors()

      task.testLogging { logging ->
        logging.events("passed", "skipped", "failed")
        logging.exceptionFormat = FULL
        logging.showCauses = true
        logging.showExceptions = true
        logging.showStackTraces = true
        logging.showStandardStreams = false
      }
    }
  }

  private fun KotlinCompile.compileClasspathConfiguration(): Configuration? {

    if (this is KaptGenerateStubsTask) return null

    // The task's `sourceSetName` property is set late in Android projects,
    // but we can just parse the name out of the task's name.
    val ssName = name
      .substringAfter("compile")
      .substringBefore("Kotlin")
      .replaceFirstChar { it.lowercase(Locale.US) }

    val compileClasspathName = when {
      ssName.isEmpty() -> "compileClasspath"
      else -> "${ssName}CompileClasspath"
    }

    return project.configurations.getByName(compileClasspathName)
  }
}
