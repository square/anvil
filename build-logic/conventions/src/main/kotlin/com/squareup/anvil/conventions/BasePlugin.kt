package com.squareup.anvil.conventions

import com.rickbusarow.kgx.extras
import com.rickbusarow.kgx.javaExtension
import com.squareup.anvil.conventions.utils.isInAnvilBuild
import com.squareup.anvil.conventions.utils.isInAnvilIncludedBuild
import com.squareup.anvil.conventions.utils.isInAnvilRootBuild
import com.squareup.anvil.conventions.utils.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePluginWrapper
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.Properties

abstract class BasePlugin : Plugin<Project> {

  open fun beforeApply(target: Project) = Unit
  open fun afterApply(target: Project) = Unit

  abstract fun Project.jvmTargetInt(): Int

  final override fun apply(target: Project) {

    val extension = target.extensions.create("conventions", ConventionsExtension::class.java)

    if (!target.isInAnvilBuild()) {
      target.copyRootProjectGradleProperties()
    }

    beforeApply(target)

    target.plugins.apply("base")

    target.plugins.apply(KtlintConventionPlugin::class.java)

    configureGradleProperties(target)

    configureBuildDirs(target)

    configureJava(target)

    target.plugins.withType(KotlinBasePluginWrapper::class.java) {
      configureKotlin(target, extension)
    }

    configureTests(target)

    target.configurations.configureEach { config ->
      config.resolutionStrategy {
        it.force(target.libs.kotlin.metadata)
      }
    }

    afterApply(target)
  }

  /**
   * Included builds need the `GROUP` and `VERSION_NAME` values from the main build's
   * `gradle.properties`. We can't just use a symlink because Windows exists.
   * See https://github.com/square/anvil/pull/763#discussion_r1379563691
   */
  private fun Project.copyRootProjectGradleProperties() {
    rootProject.file("../../gradle.properties")
      .inputStream()
      .use { Properties().apply { load(it) } }
      .forEach { key, value ->
        extras.set(key.toString(), value.toString())
      }
  }

  private fun configureGradleProperties(target: Project) {
    target.version = target.property("VERSION_NAME") as String
    target.group = target.property("GROUP") as String
  }

  private fun configureKotlin(
    target: Project,
    extension: ConventionsExtension,
  ) {

    target.tasks.withType(KotlinCompile::class.java).configureEach { task ->
      task.compilerOptions {
        allWarningsAsErrors.set(
          target.libs.versions.config.warningsAsErrors.get().toBoolean() ||
            extension.warningsAsErrors.get(),
        )

        // Only add the experimental opt-in if the project has the `annotations` dependency,
        // otherwise the compiler will throw a warning and fail in CI.
        if (target.hasAnnotationDependency()) {
          freeCompilerArgs.add("-opt-in=com.squareup.anvil.annotations.ExperimentalAnvilApi")
        }

        freeCompilerArgs.addAll(extension.kotlinCompilerArgs.get())
        if (extension.explicitApi.get()) {
          freeCompilerArgs.add("-Xexplicit-api=strict")
        }

        val fromInt = when (val targetInt = target.jvmTargetInt()) {
          8 -> JVM_1_8
          else -> JvmTarget.fromTarget("$targetInt")
        }
        jvmTarget.set(fromInt)

        if (target.libs.versions.kotlin.get().startsWith("2")) {
          // TODO: Force 1.9 until we add K2 support (https://github.com/square/anvil/issues/733)
          task.project.logger.lifecycle("Falling back to Kotlin language version 1.9")
          languageVersion.set(KOTLIN_1_9)
        }
      }
    }
  }

  /**
   * The "anvil" projects exist in two different builds, they need two different build directories
   * so that there is no shared mutable state.
   */
  private fun configureBuildDirs(target: Project) {
    when {
      !target.isInAnvilBuild() -> return

      target.isInAnvilRootBuild() -> {
        target.layout.buildDirectory.set(target.file("build/root-build"))
      }

      target.isInAnvilIncludedBuild() -> {
        target.layout.buildDirectory.set(target.file("build/included-build"))
      }
    }
  }

  /**
   * This is an imperfect but pretty good heuristic
   * to determine if the receiver has the `annotations` dependency,
   * without actually resolving the dependency graph.
   */
  private fun Project.hasAnnotationDependency(): Boolean {
    val seed = sequenceOf(
      "compileClasspath",
      "testCompileClasspath",
      "debugCompileClasspath",
    )
      .mapNotNull { configurations.findByName(it) }

    val configs = generateSequence(seed) { configs ->
      configs.flatMap { it.extendsFrom }
        .mapNotNull { configurations.findByName(it.name) }
        .takeIf { it.iterator().hasNext() }
    }
      .flatten()
      .distinct()

    // The -api and -utils projects declare the annotations as an `api` dependency.
    val providingProjects = setOf("annotations", "compiler-api", "compiler-utils")

    return configs.any { cfg ->
      cfg.dependencies.any { dep ->

        dep.group == "com.squareup.anvil" && dep.name in providingProjects
      }
    }
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

  private fun configureTests(target: Project) {
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
}
