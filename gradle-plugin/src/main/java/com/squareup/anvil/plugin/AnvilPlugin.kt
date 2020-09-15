package com.squareup.anvil.plugin

import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.TestExtension
import com.android.build.gradle.TestedExtension
import com.android.build.gradle.api.BaseVariant
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.AppliedPlugin
import org.gradle.api.plugins.PluginManager
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.internal.KaptGenerateStubsTask
import org.jetbrains.kotlin.gradle.plugin.KaptExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper
import org.jetbrains.kotlin.gradle.plugin.PLUGIN_CLASSPATH_CONFIGURATION_NAME
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.Locale.US
import java.util.concurrent.atomic.AtomicBoolean

open class AnvilPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.extensions.create("anvil", AnvilExtension::class.java)

    val once = AtomicBoolean()

    fun PluginManager.withPluginOnce(
      id: String,
      action: (AppliedPlugin) -> Unit
    ) {
      withPlugin(id) {
        if (once.compareAndSet(false, true)) {
          action(it)
        }
      }
    }

    // Apply the Anvil plugin after the Kotlin plugin was applied. There could be a timing
    // issue. Also make sure to apply it only once. A module could accidentally apply the JVM and
    // Android Kotlin plugin.
    project.pluginManager.withPluginOnce("org.jetbrains.kotlin.android") {
      realApply(project, true)
    }
    project.pluginManager.withPluginOnce("org.jetbrains.kotlin.jvm") {
      realApply(project, false)
    }

    project.afterEvaluate {
      if (!once.get()) {
        throw GradleException(
            "No supported plugins for Anvil found on project " +
                "'${project.path}'. Only Android and Java modules are supported for now."
        )
      }
    }
  }

  private fun realApply(
    project: Project,
    isAndroidProject: Boolean
  ) {
    disableIncrementalKotlinCompilation(project, isAndroidProject)
    disablePreciseJavaTracking(project)

    project.pluginManager.withPlugin("org.jetbrains.kotlin.kapt") {
      // This needs to be disabled, otherwise compiler plugins fail in weird ways when generating stubs.
      project.extensions.findByType(KaptExtension::class.java)?.correctErrorTypes = false
    }

    project.dependencies.add("implementation", "$GROUP:annotations:$VERSION")
  }

  private fun disablePreciseJavaTracking(
    project: Project
  ) {
    project.tasks
        .withType(KotlinCompile::class.java)
        .configureEach { compileTask ->
          val result = CheckMixedSourceSet.preparePreciseJavaTrackingCheck(compileTask)

          compileTask.doFirst {
            // Disable precise java tracking if needed. Note that the doFirst() action only runs
            // if the task is not up to date. That's ideal, because if nothing needs to be
            // compiled, then we don't need to disable the flag.
            //
            // We also use the doFirst block to walk through the file system at execution time
            // and minimize the IO at configuration time.
            CheckMixedSourceSet.disablePreciseJavaTrackingIfNeeded(compileTask, result)

            compileTask.logger.info(
                "Anvil: Use precise java tracking: ${compileTask.usePreciseJavaTracking}"
            )
          }
        }
  }

  @OptIn(ExperimentalStdlibApi::class)
  private fun disableIncrementalKotlinCompilation(
    project: Project,
    isAndroidProject: Boolean
  ) {
    project.tasks
        .withType(KaptGenerateStubsTask::class.java)
        .configureEach { stubsTask ->
          // Disable incremental compilation for the stub generating task. Trigger the compiler
          // plugin if any dependencies in the compile classpath have changed. This will make sure
          // that we pick up any change from a dependency when merging all the classes. Without
          // this workaround we could make changes in any library, but these changes wouldn't be
          // contributed to the Dagger graph, because incremental compilation tricked us.
          stubsTask.doFirst {
            stubsTask.incremental = false
            stubsTask.logger.info(
                "Anvil: Incremental compilation enabled: ${stubsTask.incremental} (stub)"
            )
          }
        }

    // Use this signal to share state between DisableIncrementalCompilationTask and the Kotlin
    // compile task. If the plugin classpath changed, then DisableIncrementalCompilationTask sets
    // the signal to false.
    @Suppress("UnstableApiUsage")
    val incrementalSignal = IncrementalSignal.registerIfAbsent(project)
    if (isAndroidProject) {
      project.androidVariantsConfigure { variant ->
        val compileTaskName = "compile${variant.name.capitalize(US)}Kotlin"
        disableIncrementalCompilationAction(project, incrementalSignal, compileTaskName)
      }
    } else {
      // The Java plugin has two Kotlin tasks we care about: compileKotlin and compileTestKotlin.
      disableIncrementalCompilationAction(project, incrementalSignal, "compileKotlin")
      disableIncrementalCompilationAction(project, incrementalSignal, "compileTestKotlin")
    }
  }

  private fun disableIncrementalCompilationAction(
    project: Project,
    incrementalSignal: Provider<IncrementalSignal>,
    compileTaskName: String
  ) {
    // Disable incremental compilation, if the compiler plugin dependency isn't up-to-date.
    // This will trigger a full compilation of a module using Anvil even though its
    // source files might not have changed. This workaround is necessary, otherwise
    // incremental builds are broken. See https://youtrack.jetbrains.com/issue/KT-38570
    val disableIncrementalCompilationTaskProvider = project.tasks.register(
        compileTaskName + "CheckIncrementalCompilationAnvil",
        DisableIncrementalCompilationTask::class.java
    ) { task ->
      task.pluginClasspath.from(
          project.configurations.getByName(PLUGIN_CLASSPATH_CONFIGURATION_NAME)
      )
      task.incrementalSignal.set(incrementalSignal)
    }

    project.tasks.named(compileTaskName, KotlinCompile::class.java) { compileTask ->
      compileTask.dependsOn(disableIncrementalCompilationTaskProvider)
    }

    // We avoid a reference to the project in the doFirst.
    val projectPath = project.path

    // If we merge the block below and the block above, it looks like
    // the kotlin compiler is generating byte code for
    // disableIncrementalCompilationTaskProvider to be visible from the doFirst block

    project.tasks.named(compileTaskName, KotlinCompile::class.java) { compileTask ->
      compileTask.doFirst {
        // If the signal is set, then the plugin classpath changed. Apply the setting that
        // DisableIncrementalCompilationTask requested.
        val incremental = incrementalSignal.get().isIncremental(projectPath)
        if (incremental != null) {
          compileTask.incremental = incremental
        }

        compileTask.logger.info(
            "Anvil: Incremental compilation enabled: ${compileTask.incremental} (compile)"
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

/**
 * Runs the given [action] for each Android variant including androidTest and unit test variants.
 */
fun Project.androidVariantsConfigure(action: (BaseVariant) -> Unit) {
  val androidExtension = project.extensions.findByName("android")

  if (androidExtension is AppExtension) {
    androidExtension.applicationVariants.configureEach(action)
  }
  if (androidExtension is LibraryExtension) {
    androidExtension.libraryVariants.configureEach(action)
  }
  if (androidExtension is TestExtension) {
    androidExtension.applicationVariants.configureEach(action)
  }
  if (androidExtension is TestedExtension) {
    androidExtension.unitTestVariants.configureEach(action)
    androidExtension.testVariants.configureEach(action)
  }
}

@OptIn(ExperimentalStdlibApi::class)
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
