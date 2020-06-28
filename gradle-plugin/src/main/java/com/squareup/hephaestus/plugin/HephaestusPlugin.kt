package com.squareup.hephaestus.plugin

import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.api.BaseVariant
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.AppliedPlugin
import org.jetbrains.kotlin.gradle.internal.KaptGenerateStubsTask
import org.jetbrains.kotlin.gradle.plugin.KaptExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.Locale.US
import java.util.concurrent.atomic.AtomicBoolean

open class HephaestusPlugin : Plugin<Project> {
  @ExperimentalStdlibApi
  override fun apply(project: Project) {
    val once = AtomicBoolean()
    val innerApply = Action<AppliedPlugin> {
      if (once.compareAndSet(false, true)) {
        realApply(project)
      }
    }
    with(project.pluginManager) {
      withPlugin("org.jetbrains.kotlin.android", innerApply)
      withPlugin("org.jetbrains.kotlin.jvm", innerApply)
    }
    project.afterEvaluate {
      if (!once.get()) {
        throw GradleException(
            "No supported plugins for Hephaestus found on project " +
                "'${project.path}'. Only Android and Java modules are supported for now."
        )
      }
    }
  }

  @ExperimentalStdlibApi
  private fun realApply(project: Project) {
    disableIncrementalKotlinCompilation(project)
    disablePreciseJavaTracking(project)

    project.pluginManager.withPlugin("org.jetbrains.kotlin.kapt") {
      // This needs to be disabled, otherwise compiler plugins fail in weird ways when generating stubs.
      project.extensions.findByType(KaptExtension::class.java)?.correctErrorTypes = false
    }

    project.dependencies.add("api", "$GROUP:annotations:$VERSION")
  }

  @ExperimentalStdlibApi
  private fun disablePreciseJavaTracking(
    project: Project
  ) {
    project.tasks
        .withType(KotlinCompile::class.java)
        .configureEach { compileTask ->
          compileTask.doFirst {
            // Disable precise java tracking if needed. Note that the doFirst() action only runs
            // if the task is not up to date. That's ideal, because if nothing needs to be
            // compiled, then we don't need to disable the flag.
            CheckMixedSourceSet(project, compileTask).disablePreciseJavaTrackingIfNeeded()

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
          if (compileTask is KaptGenerateStubsTask) {
            // Disable incremental compilation for the stub generating task. If any of the
            // dependencies in the compile classpath have changed, then this we need to trigger the
            // compiler plugin. This will make sure that we pick up any change from a dependency
            // when merging all the classes. Without this workaround we could make changes in any
            // library, but these changes wouldn't be contributed to the Dagger graph, because
            // incremental compilation tricked us.
            compileTask.doFirst {
              compileTask.incremental = false
              compileTask.logger.info(
                  "Hephaestus: Incremental compilation enabled: ${compileTask.incremental} (stub)"
              )
            }
            return@all
          }

          // Disable incremental compilation, if the compiler plugin dependency isn't up-to-date.
          // This will trigger a full compilation of a module using Hephaestus even though its
          // source files might not have changed. This workaround is necessary, otherwise
          // incremental builds are broken. See https://youtrack.jetbrains.com/issue/KT-38570
          val name = compileTask.name + "CheckIncrementalCompilationHephaestus"
          compileTask.dependsOn(
              project.tasks.register(name, DisableIncrementalCompilationTask::class.java)
          )

          compileTask.doFirst {
            val task = project.tasks.getByName(name) as DisableIncrementalCompilationTask
            if (task.didClassPathChange) {
              compileTask.incremental = false
            }

            compileTask.logger.info(
                "Hephaestus: Incremental compilation enabled: ${compileTask.incremental} (compile)"
            )
          }
        }
  }
}

/**
 * Returns all variants including the androidTest and unit test variants.
 */
fun Project.androidVariants(): Set<BaseVariant> {
  return when (val androidExtension = project.extensions.findByName("android")) {
    is AppExtension -> androidExtension.applicationVariants + androidExtension.testVariants +
        androidExtension.unitTestVariants
    is LibraryExtension -> androidExtension.libraryVariants + androidExtension.testVariants +
        androidExtension.unitTestVariants
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

@Suppress("SENSELESS_COMPARISON")
private val AGP_ON_CLASSPATH = try {
  Class.forName("com.android.build.gradle.AppPlugin") != null
} catch (t: Throwable) {
  false
}