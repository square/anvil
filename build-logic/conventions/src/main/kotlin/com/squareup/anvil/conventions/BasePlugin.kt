package com.squareup.anvil.conventions

import com.rickbusarow.kgx.extras
import com.rickbusarow.kgx.fromInt
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

        val sourceSetName = task.sourceSetName.getOrElse(
          task.name.substringAfter("compile")
            .substringBefore("Kotlin")
            .replaceFirstChar(Char::lowercase),
        )

        // Only add the experimental opt-in if the project has the `annotations` dependency,
        // otherwise the compiler will throw a warning and fail in CI.
        if (target.hasAnnotationDependency(sourceSetName)) {
          freeCompilerArgs.add("-opt-in=com.squareup.anvil.annotations.ExperimentalAnvilApi")
        }

        freeCompilerArgs.addAll(extension.kotlinCompilerArgs.get())

        fun isTestSourceSet(): Boolean {
          val regex = """(?:gradle|Unit|[aA]ndroid)Test""".toRegex()
          return sourceSetName == "test" || sourceSetName.matches(regex)
        }

        if (extension.explicitApi.get() && !isTestSourceSet()) {
          freeCompilerArgs.add("-Xexplicit-api=strict")
        }

        jvmTarget.set(JvmTarget.fromInt(target.jvmTargetInt()))
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
  private fun Project.hasAnnotationDependency(sourceSetName: String): Boolean {

    val compileClasspath = when (sourceSetName) {
      "main" -> "compileClasspath"
      else -> "${sourceSetName}CompileClasspath"
    }
      .let { configurations.findByName(it) }
      ?: return false

    val configs = generateSequence(sequenceOf(compileClasspath)) { configs ->
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
    // Sets the toolchain and target versions for java compilation. This waits for the 'java-base'
    // plugin instead of just 'java' for the sake of the KMP integration test project.
    target.plugins.withId("java-base") {
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
        logging.events("skipped", "failed")
        logging.exceptionFormat = FULL
        logging.showCauses = true
        logging.showExceptions = true
        logging.showStackTraces = true
        logging.showStandardStreams = false
      }
    }
  }
}
