package com.squareup.anvil.plugin

import com.android.build.gradle.AppExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.TestExtension
import com.android.build.gradle.TestedExtension
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.api.TestVariant
import com.android.build.gradle.api.UnitTestVariant
import com.android.build.gradle.internal.tasks.factory.dependsOn
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.UnknownTaskException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.internal.KaptGenerateStubsTask
import org.jetbrains.kotlin.gradle.plugin.FilesSubpluginOption
import org.jetbrains.kotlin.gradle.plugin.KaptExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType.androidJvm
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType.jvm
import org.jetbrains.kotlin.gradle.plugin.PLUGIN_CLASSPATH_CONFIGURATION_NAME
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmAndroidCompilation
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.File
import java.util.Locale.US
import java.util.concurrent.ConcurrentHashMap

@Suppress("unused")
internal open class AnvilPlugin : KotlinCompilerPluginSupportPlugin {

  private val variantCache = ConcurrentHashMap<String, Variant>()

  override fun apply(target: Project) {
    target.extensions.create("anvil", AnvilExtension::class.java)

    // Create a configuration for collecting CodeGenerator dependencies. We need to create all
    // configurations eagerly and cannot wait for applyToCompilation(..) below, because this
    // function is called in an afterEvaluate block by the Kotlin Gradle Plugin. That's too late
    // to register the configurations.
    val commonConfiguration = getConfiguration(target, buildType = "")

    // anvilTest is the common test configuration similar to other configurations like kaptTest.
    // Android build type specific unit test variants will extend this variant below. For the JVM
    // this single variant is enough.
    val testConfiguration = getConfiguration(target, buildType = "test").apply {
      extendsFrom(commonConfiguration)
    }

    agpPlugins.forEach { agpPlugin ->
      target.pluginManager.withPlugin(agpPlugin) {
        // This is the common android test variant, similar to anvilTest above.
        val androidTestVariant = getConfiguration(target, buildType = "androidTest").apply {
          extendsFrom(commonConfiguration)
        }

        target.androidVariantsConfigure { variant ->
          // E.g. "anvilDebug", "anvilTestRelease", ...
          val configuration = getConfiguration(target, buildType = variant.name)

          when (variant) {
            is UnitTestVariant -> configuration.extendsFrom(testConfiguration)
            is TestVariant -> configuration.extendsFrom(androidTestVariant)
            // non-test variants like "debug" extend the main config
            else -> configuration.extendsFrom(commonConfiguration)
          }
        }
      }
    }
  }

  override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean {
    return when (kotlinCompilation.platformType) {
      // If the variant is ignored, then don't apply the compiler plugin.
      androidJvm, jvm -> !getVariant(kotlinCompilation).variantFilter.ignore
      else -> false
    }
  }

  override fun applyToCompilation(
    kotlinCompilation: KotlinCompilation<*>
  ): Provider<List<SubpluginOption>> {
    val variant = getVariant(kotlinCompilation)
    val project = variant.project

    if (!variant.variantFilter.generateDaggerFactories &&
      variant.variantFilter.generateDaggerFactoriesOnly
    ) {
      throw GradleException(
        "You cannot set generateDaggerFactories to false and generateDaggerFactoriesOnly " +
          "to true at the same time for variant ${variant.name}."
      )
    }

    // Make the kotlin compiler classpath extend our configurations to pick up our extra
    // generators.
    project.configurations.getByName(variant.compilerPluginClasspathName)
      .extendsFrom(getConfiguration(project, variant.name))

    disableIncrementalKotlinCompilation(variant)
    disablePreciseJavaTracking(variant)

    if (!variant.variantFilter.generateDaggerFactoriesOnly) {
      disableCorrectErrorTypes(variant)

      kotlinCompilation.dependencies {
        implementation("$GROUP:annotations:$VERSION")
      }
    }

    // Notice that we use the name of the variant as a directory name. Generated code
    // for this specific compile task will be included in the task output. The output of different
    // compile tasks shouldn't be mixed.
    val srcGenDir = File(
      project.buildDir,
      "anvil${File.separator}src-gen-${variant.name}"
    )

    return project.provider {
      listOf(
        FilesSubpluginOption(
          key = "src-gen-dir",
          files = listOf(srcGenDir)
        ),
        SubpluginOption(
          key = "generate-dagger-factories",
          lazy { variant.variantFilter.generateDaggerFactories.toString() }
        ),
        SubpluginOption(
          key = "generate-dagger-factories-only",
          lazy { variant.variantFilter.generateDaggerFactoriesOnly.toString() }
        ),
        SubpluginOption(
          key = "disable-component-merging",
          lazy { variant.variantFilter.disableComponentMerging.toString() }
        )
      )
    }
  }

  override fun getCompilerPluginId(): String = "com.squareup.anvil.compiler"

  override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
    groupId = GROUP,
    artifactId = "compiler",
    version = VERSION
  )

  private fun disablePreciseJavaTracking(variant: Variant) {
    variant.compileTaskProvider.configure { compileTask ->
      val result = CheckMixedSourceSet.preparePreciseJavaTrackingCheck(variant)

      compileTask.doFirstCompat {
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

  private fun disableCorrectErrorTypes(variant: Variant) {
    variant.project.pluginManager.withPlugin("org.jetbrains.kotlin.kapt") {
      // This needs to be disabled, otherwise compiler plugins fail in weird ways when
      // generating stubs, e.g.:
      //
      // /anvil/sample/app/build/generated/source/kapt/debug/com/squareup/anvil
      // /sample/DaggerAppComponent.java:13: error: DaggerAppComponent is not abstract and does
      // not override abstract method string() in RandomComponent
      // public final class DaggerAppComponent implements AppComponent {
      //              ^
      // 1 error
      variant.project.extensions.findByType(KaptExtension::class.java)?.correctErrorTypes = false
    }
  }

  private fun disableIncrementalKotlinCompilation(variant: Variant) {
    // Use this signal to share state between DisableIncrementalCompilationTask and the Kotlin
    // compile task. If the plugin classpath changed, then DisableIncrementalCompilationTask sets
    // the signal to false.
    @Suppress("UnstableApiUsage")
    val incrementalSignal = IncrementalSignal.registerIfAbsent(variant.project)

    disableIncrementalCompilationWhenTheCompilerClasspathChanges(
      variant, incrementalSignal, variant.compileTaskProvider
    )

    variant.project.pluginManager.withPlugin("org.jetbrains.kotlin.kapt") {
      variant.project
        .namedLazy<KaptGenerateStubsTask>(
          "kaptGenerateStubs${variant.taskSuffix}"
        ) { stubsTaskProvider ->
          if (variant.variantFilter.generateDaggerFactoriesOnly ||
            variant.variantFilter.disableComponentMerging
          ) {
            // We don't need to disable the incremental compilation for the stub generating task
            // every time, when we only generate Dagger factories or contribute modules. That's only
            // needed for merging Dagger modules.
            //
            // However, we still need to update the generated code when the compiler plugin classpath
            // changes.
            disableIncrementalCompilationWhenTheCompilerClasspathChanges(
              variant, incrementalSignal, stubsTaskProvider
            )
          } else {
            stubsTaskProvider.configure { stubsTask ->
              // Disable incremental compilation for the stub generating task. Trigger the compiler
              // plugin if any dependencies in the compile classpath have changed. This will make sure
              // that we pick up any change from a dependency when merging all the classes. Without
              // this workaround we could make changes in any library, but these changes wouldn't be
              // contributed to the Dagger graph, because incremental compilation tricked us.
              stubsTask.doFirstCompat {
                stubsTask.incremental = false
                stubsTask.logger.info(
                  "Anvil: Incremental compilation enabled: ${stubsTask.incremental} (stub)"
                )
              }
            }
          }
        }
    }
  }

  private fun disableIncrementalCompilationWhenTheCompilerClasspathChanges(
    variant: Variant,
    incrementalSignal: Provider<IncrementalSignal>,
    compileTaskProvider: TaskProvider<out KotlinCompile>
  ) {
    val compileTaskName = compileTaskProvider.name

    // Disable incremental compilation, if the compiler plugin dependency isn't up-to-date.
    // This will trigger a full compilation of a module using Anvil even though its
    // source files might not have changed. This workaround is necessary, otherwise
    // incremental builds are broken. See https://youtrack.jetbrains.com/issue/KT-38570
    val disableIncrementalCompilationTaskProvider = variant.project.tasks.register(
      compileTaskName + "CheckIncrementalCompilationAnvil",
      DisableIncrementalCompilationTask::class.java
    ) { task ->
      task.pluginClasspath.from(
        variant.project.configurations.getByName(variant.compilerPluginClasspathName)
      )
      task.incrementalSignal.set(incrementalSignal)
    }

    compileTaskProvider.dependsOn(disableIncrementalCompilationTaskProvider)

    // We avoid a reference to the project in the doFirst.
    val projectPath = variant.project.path

    compileTaskProvider.configure { compileTask ->
      compileTask.doFirstCompat {
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

  private fun getConfiguration(
    project: Project,
    buildType: String
  ): Configuration {
    val name = if (buildType.isEmpty()) "anvil" else "anvil${buildType.capitalize(US)}"
    return project.configurations.maybeCreate(name).apply {
      description = "This configuration is used for dependencies with Anvil CodeGenerator " +
        "implementations."
    }
  }

  private fun getVariant(kotlinCompilation: KotlinCompilation<*>): Variant {
    return variantCache.computeIfAbsent(kotlinCompilation.name) {
      val variant = Variant(kotlinCompilation)

      // The cache makes sure that we execute this filter action only once.
      variant.project.extensions.getByType(AnvilExtension::class.java)
        ._variantFilter
        ?.execute(variant.variantFilter)

      variant
    }
  }
}

/*
 * Don't convert this to a lambda to avoid this error:
 *
 * The task was not up-to-date because of the following reasons:
 * Additional action for task ':sample:library:compileKotlin': was implemented by the Java
 * lambda 'com.squareup.anvil.plugin.AnvilPlugin$$Lambda$7578/0x0000000801769440'. Using Java
 * lambdas is not supported, use an (anonymous) inner class instead.
 *
 * Sample build scan: https://scans.gradle.com/s/ppwb2psllveks/timeline?details=j62m2xx52wwxy
 *
 * More context is here: https://github.com/gradle/gradle/issues/5510#issuecomment-416860213
 */
@Suppress("ObjectLiteralToLambda")
private fun <T : Task> T.doFirstCompat(block: (T) -> Unit) {
  doFirst(object : Action<Task> {
    override fun execute(task: Task) {
      @Suppress("UNCHECKED_CAST")
      block(task as T)
    }
  })
}

/**
 * Similar to [TaskContainer.named], but waits until the task is registered if it doesn't exist,
 * yet. If the task is never registered, then this method will throw an error after the
 * configuration phase.
 */
private inline fun <reified T : Task> Project.namedLazy(
  name: String,
  crossinline action: (TaskProvider<T>) -> Unit
) {
  try {
    action(tasks.named(name, T::class.java))
    return
  } catch (ignored: UnknownTaskException) {
  }

  var didRun = false

  tasks.whenTaskAdded { task ->
    if (task.name == name && task is T) {
      action(tasks.named(name, T::class.java))
      didRun = true
    }
  }

  afterEvaluate {
    if (!didRun) {
      throw GradleException("Didn't find task $name with type ${T::class}.")
    }
  }
}

/**
 * Runs the given [action] for each Android variant including androidTest and unit test variants.
 */
private fun Project.androidVariantsConfigure(action: (BaseVariant) -> Unit) {
  val androidExtension = extensions.findByName("android")

  when (androidExtension) {
    is AppExtension -> androidExtension.applicationVariants.configureEach(action)
    is LibraryExtension -> androidExtension.libraryVariants.configureEach(action)
    is TestExtension -> androidExtension.applicationVariants.configureEach(action)
  }

  if (androidExtension is TestedExtension) {
    androidExtension.unitTestVariants.configureEach(action)
    androidExtension.testVariants.configureEach(action)
  }
}

private val agpPlugins = listOf(
  "com.android.library",
  "com.android.application",
  "com.android.test",
  "com.android.dynamic-feature",
)

internal class Variant private constructor(
  val name: String,
  val project: Project,
  val compileTaskProvider: TaskProvider<KotlinCompile>,
  val androidVariant: BaseVariant?,
  val compilerPluginClasspathName: String,
  val variantFilter: VariantFilter,
) {
  // E.g. compileKotlin, compileKotlinJvm, compileDebugKotlin.
  val taskSuffix = compileTaskProvider.name.substringAfter("compile")

  companion object {
    operator fun invoke(kotlinCompilation: KotlinCompilation<*>): Variant {
      // Sanity check.
      require(
        kotlinCompilation.platformType != androidJvm ||
          kotlinCompilation is KotlinJvmAndroidCompilation
      ) {
        "The KotlinCompilation is KotlinJvmAndroidCompilation, but the platform type " +
          "is different."
      }

      val project = kotlinCompilation.target.project
      val extension = project.extensions.getByType(AnvilExtension::class.java)
      val androidVariant = (kotlinCompilation as? KotlinJvmAndroidCompilation)?.androidVariant

      val commonFilter = CommonFilter(kotlinCompilation.name, extension)
      val variantFilter = if (androidVariant != null) {
        AndroidVariantFilter(commonFilter, androidVariant)
      } else {
        JvmVariantFilter(commonFilter)
      }

      @Suppress("UNCHECKED_CAST")
      return Variant(
        name = kotlinCompilation.name,
        project = project,
        compileTaskProvider = kotlinCompilation.compileKotlinTaskProvider as
          TaskProvider<KotlinCompile>,
        androidVariant = androidVariant,
        compilerPluginClasspathName = PLUGIN_CLASSPATH_CONFIGURATION_NAME +
          kotlinCompilation.target.targetName.capitalize(US) +
          kotlinCompilation.name.capitalize(US),
        variantFilter = variantFilter
      ).also {
        // Sanity check.
        check(it.compileTaskProvider.name.startsWith("compile"))
      }
    }
  }
}
