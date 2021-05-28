package com.squareup.anvil.plugin

import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.TestExtension
import com.android.build.gradle.TestedExtension
import com.android.build.gradle.api.BaseVariant
import com.squareup.anvil.plugin.ProjectType.ANDROID
import com.squareup.anvil.plugin.ProjectType.JVM
import com.squareup.anvil.plugin.ProjectType.MULTIPLATFORM_ANDROID
import com.squareup.anvil.plugin.ProjectType.MULTIPLATFORM_JVM
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.plugins.AppliedPlugin
import org.gradle.api.plugins.PluginManager
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.internal.KaptGenerateStubsTask
import org.jetbrains.kotlin.gradle.plugin.FilesSubpluginOption
import org.jetbrains.kotlin.gradle.plugin.KaptExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType.androidJvm
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType.jvm
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper
import org.jetbrains.kotlin.gradle.plugin.PLUGIN_CLASSPATH_CONFIGURATION_NAME
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.File
import java.util.Locale.US
import java.util.concurrent.atomic.AtomicBoolean

@Suppress("unused")
open class AnvilPlugin : KotlinCompilerPluginSupportPlugin {

  override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean {
    return kotlinCompilation.target.project.plugins.hasPlugin(AnvilPlugin::class.java)
  }

  override fun getCompilerPluginId(): String = "com.squareup.anvil.compiler"

  override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
    groupId = GROUP,
    artifactId = "compiler",
    version = VERSION
  )

  override fun applyToCompilation(
    kotlinCompilation: KotlinCompilation<*>
  ): Provider<List<SubpluginOption>> {
    val project = kotlinCompilation.target.project

    // Notice that we use the name of the Kotlin compilation as a directory name. Generated code
    // for this specific compile task will be included in the task output. The output of different
    // compile tasks shouldn't be mixed.
    val srcGenDir = File(
      project.buildDir,
      "anvil${File.separator}src-gen-${kotlinCompilation.name}"
    )
    val extension = project.extensions.findByType(AnvilExtension::class.java)
      ?: project.objects.newInstance(AnvilExtension::class.java)

    if (extension.addGeneratedDirToSourceSets.get()) {
      kotlinCompilation.defaultSourceSet {
        kotlin.srcDir(srcGenDir)
      }
    }

    return project.provider {
      listOf(
        FilesSubpluginOption(
          key = "src-gen-dir",
          files = listOf(srcGenDir)
        ),
        SubpluginOption(
          key = "generate-dagger-factories",
          lazy { extension.generateDaggerFactories.get().toString() }
        ),
        SubpluginOption(
          key = "generate-dagger-factories-only",
          lazy { extension.generateDaggerFactoriesOnly.get().toString() }
        ),
        SubpluginOption(
          key = "disable-component-merging",
          lazy { extension.disableComponentMerging.get().toString() }
        )
      )
    }
  }

  override fun apply(target: Project) {
    val extension = target.extensions.create("anvil", AnvilExtension::class.java)

    // Create a configuration for collecting CodeGenerator dependencies.
    val anvilConfiguration = target.configurations.maybeCreate("anvil").apply {
      description = "This configuration is used for dependencies with Anvil CodeGenerator " +
        "implementations."
    }

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
    target.pluginManager.withPluginOnce("org.jetbrains.kotlin.android") {
      realApply(target, ANDROID, extension, anvilConfiguration)
    }
    target.pluginManager.withPluginOnce("org.jetbrains.kotlin.multiplatform") {
      target.afterEvaluate {
        // We need to run this in afterEvaluate, because otherwise the multiplatform extension
        // isn't initialized.
        val targets =
          target.extensions.findByType(KotlinMultiplatformExtension::class.java)?.targets

        when {
          targets?.any { it.platformType == androidJvm } == true ->
            realApply(target, MULTIPLATFORM_ANDROID, extension, anvilConfiguration)
          targets?.any { it.platformType == jvm } == true ->
            realApply(target, MULTIPLATFORM_JVM, extension, anvilConfiguration)
          else -> throw GradleException(
            "Multiplatform project $target is not setup for Android or JVM."
          )
        }
      }
    }
    target.pluginManager.withPluginOnce("org.jetbrains.kotlin.jvm") {
      realApply(target, JVM, extension, anvilConfiguration)
    }

    target.afterEvaluate {
      if (!once.get()) {
        throw GradleException(
          "No supported plugins for Anvil found on project " +
            "'${target.path}'. Only Android and Java modules are supported for now."
        )
      }

      if (!extension.generateDaggerFactories.get() &&
        extension.generateDaggerFactoriesOnly.get()
      ) {
        throw GradleException(
          "You cannot set generateDaggerFactories to false and generateDaggerFactoriesOnly " +
            "to true at the same time."
        )
      }
    }
  }

  private fun realApply(
    project: Project,
    projectType: ProjectType,
    extension: AnvilExtension,
    anvilConfiguration: Configuration
  ) {
    disableIncrementalKotlinCompilation(project, projectType, extension)
    disablePreciseJavaTracking(project)

    project.afterEvaluate {
      if (!extension.generateDaggerFactoriesOnly.get()) {
        project.pluginManager.withPlugin("org.jetbrains.kotlin.kapt") {
          // This needs to be disabled, otherwise compiler plugins fail in weird ways when generating stubs.
          project.extensions.findByType(KaptExtension::class.java)?.correctErrorTypes = false
        }

        project.dependencies.add("implementation", "$GROUP:annotations:$VERSION")
      }
    }

    // Make the kotlin compiler classpath extend our configuration to pick up our extra generators.
    project.configurations.getByName(PLUGIN_CLASSPATH_CONFIGURATION_NAME)
      .extendsFrom(anvilConfiguration)
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

  private fun disableIncrementalKotlinCompilation(
    project: Project,
    projectType: ProjectType,
    extension: AnvilExtension
  ) {
    // Use this signal to share state between DisableIncrementalCompilationTask and the Kotlin
    // compile task. If the plugin classpath changed, then DisableIncrementalCompilationTask sets
    // the signal to false.
    @Suppress("UnstableApiUsage")
    val incrementalSignal = IncrementalSignal.registerIfAbsent(project)

    val taskSuffix = when (projectType) {
      JVM, ANDROID -> "Kotlin"
      MULTIPLATFORM_JVM -> "KotlinJvm"
      MULTIPLATFORM_ANDROID -> "KotlinAndroid"
    }

    if (extension.generateDaggerFactoriesOnly.get() ||
      extension.disableComponentMerging.get()
    ) {
      // We don't need to disable the incremental compilation for the stub generating task, when we
      // only generate Dagger factories or contributing modules. That's only needed for merging
      // Dagger modules.
      if (projectType.isAndroid) {
        project.androidVariantsConfigure { variant ->
          val compileTaskName = "kaptGenerateStubs${variant.name.capitalize(US)}$taskSuffix"
          disableIncrementalCompilationAction(project, incrementalSignal, compileTaskName)
        }
      } else {
        disableIncrementalCompilationAction(
          project,
          incrementalSignal,
          "kaptGenerateStubs$taskSuffix"
        )
        disableIncrementalCompilationAction(
          project,
          incrementalSignal,
          "kaptGenerateStubsTest$taskSuffix"
        )
      }
    } else {
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
    }

    if (projectType.isAndroid) {
      project.androidVariantsConfigure { variant ->
        val compileTaskName = "compile${variant.name.capitalize(US)}$taskSuffix"
        disableIncrementalCompilationAction(project, incrementalSignal, compileTaskName)
      }
    } else {
      // The Java plugin has two Kotlin tasks we care about: compileKotlin and compileTestKotlin.
      disableIncrementalCompilationAction(project, incrementalSignal, "compile$taskSuffix")
      disableIncrementalCompilationAction(project, incrementalSignal, "compileTest$taskSuffix")
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
    is AppExtension ->
      androidExtension.applicationVariants + androidExtension.testVariants +
        androidExtension.unitTestVariants
    is LibraryExtension ->
      androidExtension.libraryVariants + androidExtension.testVariants +
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

fun Collection<BaseVariant>.findVariantForCompileTask(
  compileTask: KotlinCompile
): BaseVariant = this
  .filter { variant ->
    compileTask.name.contains(variant.name.capitalize(US))
  }
  .maxByOrNull {
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

private enum class ProjectType(val isAndroid: Boolean) {
  JVM(false),
  ANDROID(true),
  MULTIPLATFORM_JVM(false),
  MULTIPLATFORM_ANDROID(true),
}
