package com.squareup.hephaestus.plugin

import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.api.BaseVariant
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.internal.KaptGenerateStubsTask
import org.jetbrains.kotlin.gradle.plugin.KaptExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper
import org.jetbrains.kotlin.gradle.plugin.PLUGIN_CLASSPATH_CONFIGURATION_NAME
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.Locale.US

open class HephaestusPlugin : Plugin<Project> {
  @ExperimentalStdlibApi
  override fun apply(project: Project) {
    if (!project.isAndroidProject && !project.isKotlinJvmProject) {
      throw GradleException("Only Android and Java modules are supported for now.")
    }

    disableIncrementalKotlinCompilation(project)
    disablePreciseJavaTracking(project)

    // This needs to be disabled, otherwise compiler plugins fail in weird ways when generating stubs.
    project.extensions.findByType(KaptExtension::class.java)?.correctErrorTypes = false

    project.dependencies.add("api", "$GROUP:annotations:$VERSION")
  }

  @ExperimentalStdlibApi
  private fun disablePreciseJavaTracking(
    project: Project
  ) {
    project.tasks
        .withType(KotlinCompile::class.java)
        .all { compileTask ->
          val checkMixedSourceSet = project.tasks.register(
              compileTask.name + "CheckMixedSourceSetHephaestus",
              CheckMixedSourceSetTask::class.java
          ) {
            it.compileTask = compileTask
          }

          compileTask.dependsOn(checkMixedSourceSet)

          compileTask.doFirst {
            compileTask.logger.info(
                "Hephaestus: Use precise java tracking: ${compileTask.usePreciseJavaTracking}"
            )
          }
        }
  }

  @ExperimentalStdlibApi
  private fun disableIncrementalKotlinCompilation(
    project: Project
  ) {
    project.tasks
        .withType(KotlinCompile::class.java)
        .all { compileTask ->
          val isStubGeneratingTask = compileTask is KaptGenerateStubsTask

          if (isStubGeneratingTask) {
            check(
                compileTask.name.startsWith("kaptGenerateStubs") &&
                    compileTask.name.endsWith("Kotlin")
            ) {
              // It's better to verify this naming pattern and fail early when it changes.
              // See also: https://github.com/JetBrains/kotlin/blob/897c48f97eac4e0a5190773cd7b5c31683d601eb/libraries/tools/kotlin-gradle-plugin/src/main/kotlin/org/jetbrains/kotlin/gradle/internal/kapt/Kapt3KotlinGradleSubplugin.kt#L490
              "It's expected that the stub generating task is named " +
                  "kaptGenerateStubs{variant}{buildType}Kotlin."
            }
          }

          val configurationNameForIncrementalChecks = when {
            // Disable incremental compilation, if the compiler plugin dependency isn't up-to-date.
            // This will trigger a full compilation of a module using Hephaestus even though its
            // source files might not have changed. This workaround is necessary, otherwise
            // incremental builds are broken. See https://youtrack.jetbrains.com/issue/KT-38570
            !isStubGeneratingTask -> PLUGIN_CLASSPATH_CONFIGURATION_NAME

            // For Java / Kotlin projects it doesn't change.
            project.isKotlinJvmProject -> "compileClasspath"

            // Disable incremental compilation for the stub generating task, if any of the
            // dependencies in the compile classpath have changed. This will make sure that we pick
            // up any change from a dependency when merging all the classes. Without this workaround
            // we could make changes in any library, but these changes wouldn't be contributed to the
            // Dagger graph, because incremental compilation tricked us.
            else -> {
              val variant = project.androidVariants()
                  .findVariantForCompileTask(compileTask)

              "${variant.name}CompileClasspath"
            }
          }

          val checkIfCompilerPluginChangedTaskProvider = project.tasks
              .register(
                  compileTask.name + "CheckIncrementalCompilationHephaestus",
                  DisableIncrementalCompilationTask::class.java
              ) {
                it.compileTask = compileTask
                it.configurationName = configurationNameForIncrementalChecks
              }

          compileTask.dependsOn(checkIfCompilerPluginChangedTaskProvider)

          compileTask.doFirst {
            compileTask.logger.info(
                "Hephaestus: Incremental compilation enabled: ${compileTask.incremental}"
            )
          }
        }
  }
}

/**
 * Returns all variants including the androidTest variants, but excludes unit tests. Unit tests
 * can't contribute classes and if we need to support merging components in unit tests, then we
 * can add the capability later. YAGNI.
 */
fun Project.androidVariants(): Set<BaseVariant> {
  return when (val androidExtension = project.extensions.findByName("android")) {
    is AppExtension -> androidExtension.applicationVariants + androidExtension.testVariants
    is LibraryExtension -> androidExtension.libraryVariants + androidExtension.testVariants
    else -> throw GradleException("Unknown Android module type for project ${project.path}")
  }
}

@ExperimentalStdlibApi
fun Collection<BaseVariant>.findVariantForCompileTask(
  compileTask: KotlinCompile
): BaseVariant = this
    .filter { variant ->
      compileTask.name.contains(variant.name.capitalize(US))
    }
    .maxBy {
      // The filter above still returns multiple variants, e.g. for the
      // "compileDebugUnitTestKotlin" task it returns the variants "debug" and "debugUnitTest".
      // In this case prefer the variant with the longest matching name, because that's the more
      // explicit variant that we want.
      it.name.length
    }!!

val Project.isKotlinJvmProject: Boolean
  get() = plugins.hasPlugin(KotlinPluginWrapper::class.java)

val Project.isAndroidProject: Boolean
  get() = AGP_ON_CLASSPATH &&
      (plugins.hasPlugin(AppPlugin::class.java) || plugins.hasPlugin(LibraryPlugin::class.java))

private val AGP_ON_CLASSPATH = try {
  Class.forName("com.android.build.gradle.AppPlugin") != null
} catch (t: Throwable) {
  false
}