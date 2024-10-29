@file:Suppress("UnstableApiUsage")

package com.squareup.anvil.plugin

import com.android.build.api.dsl.AndroidSourceSet
import com.android.build.gradle.AppExtension
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.TestExtension
import com.android.build.gradle.TestedExtension
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0
import org.jetbrains.kotlin.gradle.internal.KaptGenerateStubsTask
import org.jetbrains.kotlin.gradle.plugin.FilesSubpluginOption
import org.jetbrains.kotlin.gradle.plugin.KaptExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType.androidJvm
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType.jvm
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.PLUGIN_CLASSPATH_CONFIGURATION_NAME
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmAndroidCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmCompilation
import org.jetbrains.kotlin.gradle.tasks.AbstractKotlinCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.File
import java.util.concurrent.ConcurrentHashMap

@Suppress("DEPRECATION")
internal typealias BaseVariantDeprecated = com.android.build.gradle.api.BaseVariant

@Suppress("DEPRECATION")
private typealias TestVariantDeprecated = com.android.build.gradle.api.TestVariant

@Suppress("DEPRECATION")
private typealias UnitTestVariantDeprecated = com.android.build.gradle.api.UnitTestVariant

@Suppress("unused")
internal open class AnvilPlugin : KotlinCompilerPluginSupportPlugin {

  private val variantCache = ConcurrentHashMap<String, Variant>()

  override fun apply(target: Project) {
    target.extensions.create("anvil", AnvilExtension::class.java, target)

    // TODO consider only lazily setting up these `anvil()` configurations in embedded mode?
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

    //  Wire up embedded plugins
    agpPlugins.forEach { agpPlugin ->
      target.pluginManager.withPlugin(agpPlugin) {
        // This is the common android test variant, similar to anvilTest above.
        val androidTestVariant = getConfiguration(target, buildType = "androidTest").apply {
          extendsFrom(commonConfiguration)
        }

        target.androidVariantsConfigure { variant ->
          // E.g. "anvilDebug", "anvilTestRelease", ...
          val configuration = getConfiguration(target, buildType = variant.name)

          @Suppress("TYPEALIAS_EXPANSION_DEPRECATION")
          when (variant) {
            is UnitTestVariantDeprecated -> configuration.extendsFrom(testConfiguration)
            is TestVariantDeprecated -> configuration.extendsFrom(androidTestVariant)
            // non-test variants like "debug" extend the main config
            else -> configuration.extendsFrom(commonConfiguration)
          }
        }
      }
    }

    jvmPlugins.forEach { javaPlugin ->
      target.pluginManager.withPlugin(javaPlugin) {
        // Without this connection anvil(..) dependencies won't be picked up in the main build
        // for JVM modules. We already do this for the test configuration above, which is shared
        // between JVM and Android. The main configuration is specific to the JVM.
        getConfiguration(target, "main").extendsFrom(commonConfiguration)
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
    kotlinCompilation: KotlinCompilation<*>,
  ): Provider<List<SubpluginOption>> {
    kotlinCompilation.compileTaskProvider.configure { action ->
      action.compilerOptions.let {
        @Suppress("DEPRECATION")
        val useK2 = it.useK2.get()
        if (useK2 || it.languageVersion.getOrElse(KOTLIN_1_9) >= KOTLIN_2_0) {
          kotlinCompilation.project.logger
            .warn(
              "NOTE: Anvil is currently incompatible with the K2 compiler and the language " +
                "version will be overridden to 1.9. Related GH issue:" +
                "https://github.com/square/anvil/issues/733",
            )
        }

        it.languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9)
        it.apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9)
      }
    }

    val variant = getVariant(kotlinCompilation)
    val project = variant.project

    if (!variant.variantFilter.generateDaggerFactories &&
      variant.variantFilter.generateDaggerFactoriesOnly
    ) {
      throw GradleException(
        "You cannot set generateDaggerFactories to false and generateDaggerFactoriesOnly " +
          "to true at the same time for variant ${variant.name}.",
      )
    }

    // Make the kotlin compiler classpath extend our configurations to pick up our extra
    // generators.
    project.configurations.getByName(variant.compilerPluginClasspathName)
      .extendsFrom(getConfiguration(project, variant.name))

    if (!variant.variantFilter.generateDaggerFactoriesOnly) {
      disableCorrectErrorTypes(variant)

      kotlinCompilation.dependencies {
        compileOnly("$GROUP:annotations:$VERSION")
      }
    }
    if (variant.variantFilter.addOptionalAnnotations) {
      kotlinCompilation.dependencies {
        compileOnly("$GROUP:annotations-optional:$VERSION")
      }
    }

    // Notice that we use the name of the variant as a directory name. Generated code
    // for this specific compile task will be included in the task output. The output of different
    // compile tasks shouldn't be mixed.
    val variantDir = project.layout.buildDirectory
      .dir("anvil/${variant.name}")

    val srcGenDir = variantDir.map { it.dir("generated") }

    val anvilCacheDir = variantDir.map { it.dir("caches") }

    val irMergesFile = anvilCacheDir
      .map { it.asFile.resolve("ir-merges.txt") }

    disableIncrementalKotlinCompilation(
      variant = variant,
      compileTaskProvider = kotlinCompilation.compileTaskProvider,
      irMergesFile = irMergesFile,
    )

    kotlinCompilation.compileTaskProvider.configure { task ->

      if (variant.variantFilter.trackSourceFiles) {
        // Add the generated files directory as output
        // so that Gradle will watch them and invoke the compile task if they've changed.
        // This also makes Gradle restore the files from the remote build cache,
        // but that's technically not necessary.
        task.outputs.dir(srcGenDir)
        // This adds Anvil's internal cache and source-to-generated mapping to Gradle's cache,
        // so that Gradle handles its restoration.
        // The contents of this cache are used to restore any missing generated output.
        task.outputs.dir(anvilCacheDir)
      }
    }

    if (variant.variantFilter.syncGeneratedSources) {
      val isIdeSyncProvider = project.providers
        .systemProperty("idea.sync.active")

      if (isIdeSyncProvider.getOrElse("false").toBoolean()) {
        // Only add source sets during the IDE sync. Don't add them for compilation, otherwise
        // we'll see weird compile errors especially with incremental compilation. For a longer
        // explanation why this is a bad idea, see here:
        // https://github.com/square/anvil/pull/207#issuecomment-850768750
        kotlinCompilation.defaultSourceSet {
          kotlin.srcDir(srcGenDir)
        }

        // For Android and AGP the above code doesn't work for some reason. This is the workaround.
        variant.androidSourceSets?.forEach { sourceSet ->
          sourceSet.java.srcDir(srcGenDir)
        }
      }
    }

    fun Variant.willHaveDaggerFactories(): Boolean {
      if (variantFilter.generateDaggerFactories) return true

      return kotlinCompilation.kaptConfigOrNull(project)?.hasDaggerCompilerDependency() == true
    }

    return project.provider {
      listOf(
        FilesSubpluginOption(
          key = "gradle-project-dir",
          files = listOf(project.projectDir),
        ),
        FilesSubpluginOption(
          key = "gradle-build-dir",
          files = listOf(project.layout.buildDirectory.get().asFile),
        ),
        FilesSubpluginOption(
          key = "src-gen-dir",
          files = listOf(srcGenDir.get().asFile),
        ),
        FilesSubpluginOption(
          key = "anvil-cache-dir",
          files = listOf(anvilCacheDir.get().asFile),
        ),
        FilesSubpluginOption(
          key = "ir-merges-file",
          listOf(irMergesFile.get()),
        ),
        SubpluginOption(
          key = "generate-dagger-factories",
          lazy { variant.variantFilter.generateDaggerFactories.toString() },
        ),
        SubpluginOption(
          key = "generate-dagger-factories-only",
          lazy { variant.variantFilter.generateDaggerFactoriesOnly.toString() },
        ),
        SubpluginOption(
          key = "disable-component-merging",
          lazy { variant.variantFilter.disableComponentMerging.toString() },
        ),
        SubpluginOption(
          key = "track-source-files",
          lazy { variant.variantFilter.trackSourceFiles.toString() },
        ),
        SubpluginOption(
          key = "will-have-dagger-factories",
          lazy { variant.willHaveDaggerFactories().toString() },
        ),
        SubpluginOption(
          key = "analysis-backend",
          lazy { "EMBEDDED" },
        ),
        SubpluginOption(
          key = "merging-backend",
          lazy { "IR" },
        ),
      )
    }
  }

  override fun getCompilerPluginId(): String = "com.squareup.anvil.compiler"

  override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
    groupId = GROUP,
    artifactId = "compiler",
    version = VERSION,
  )

  private fun disableCorrectErrorTypes(variant: Variant) {
    variant.project.pluginManager.withPlugin(KAPT_PLUGIN_ID) {
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

  private fun disableIncrementalKotlinCompilation(
    variant: Variant,
    compileTaskProvider: TaskProvider<out KotlinCompilationTask<*>>,
    irMergesFile: Provider<File>,
  ) {

    fun mergingIsDisabled(): Boolean {
      return variant.variantFilter.generateDaggerFactoriesOnly ||
        variant.variantFilter.disableComponentMerging
    }

    compileTaskProvider.configure { task ->

      if (variant.variantFilter.trackSourceFiles && !mergingIsDisabled()) {
        if (task is AbstractKotlinCompile<*>) {
          // The `ir-merges.txt` file indicates that the last compilation resulted in adding module
          // arguments to an annotation or adding interface supertypes.  Those changes do not affect
          // the output .class files, which means that Kotlin's incremental logic doesn't properly
          // track the changes.  So, we disable incremental compilation whenever this file exists.
          task.doFirstCompat {
            task.incremental = task.incremental && !irMergesFile.get().exists()
            task.log(
              "Anvil: Incremental compilation enabled: ${task.incremental} (stub)",
            )
          }
        }
      }
    }

    variant.project.pluginManager.withPlugin(KAPT_PLUGIN_ID) {
      variant.project.tasks
        .withType(KaptGenerateStubsTask::class.java)
        .named { it == variant.stubsTaskName }
        .configureEach { stubsTask ->
          if (!mergingIsDisabled()) {
            // Disable incremental compilation for the stub generating task. Trigger the compiler
            // plugin if any dependencies in the compile classpath have changed. This will make sure
            // that we pick up any change from a dependency when merging all the classes. Without
            // this workaround we could make changes in any library, but these changes wouldn't be
            // contributed to the Dagger graph, because incremental compilation tricked us.
            stubsTask.doFirstCompat {
              stubsTask.incremental = false
              stubsTask.log(
                "Anvil: Incremental compilation enabled: ${stubsTask.incremental} (stub)",
              )
            }
          }
        }
    }
  }

  private fun getConfiguration(
    project: Project,
    buildType: String,
  ): Configuration {
    val name =
      if (buildType.isEmpty()) "anvil" else "anvil${buildType.replaceFirstChar(Char::uppercase)}"
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
  doFirst(
    object : Action<Task> {
      override fun execute(task: Task) {
        @Suppress("UNCHECKED_CAST")
        block(task as T)
      }
    },
  )
}

/**
 * Similar to [TaskContainer.named], but waits until the task is registered if it doesn't exist,
 * yet. If the task is never registered, then this method will throw an error after the
 * configuration phase.
 */
private inline fun <reified T : Task> Project.namedLazy(name: String, action: Action<T>) {

  tasks.withType(T::class.java)
    .named { it == name }
    .configureEach(action)
}

/**
 * Runs the given [action] for each Android variant including androidTest and unit test variants.
 */

private fun Project.androidVariantsConfigure(
  @Suppress("TYPEALIAS_EXPANSION_DEPRECATION")
  action: (BaseVariantDeprecated) -> Unit,
) {
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

// I considered extending this list with 'java-library' and 'application', but they both apply
// the 'java' plugin implicitly. One could also apply the 'java' plugin alone without the
// application or library plugin, so 'java' must be included in this list.
private val jvmPlugins = listOf(
  "java",
)

private val agpPlugins = listOf(
  "com.android.library",
  "com.android.application",
  "com.android.test",
  "com.android.dynamic-feature",
)

private const val KAPT_PLUGIN_ID = "org.jetbrains.kotlin.kapt"

internal fun String.capitalize(): String = replaceFirstChar(Char::uppercaseChar)

internal class Variant private constructor(
  val name: String,
  val project: Project,
  val compileTaskProvider: TaskProvider<KotlinCompile>,
  @Suppress("TYPEALIAS_EXPANSION_DEPRECATION")
  val androidVariant: BaseVariantDeprecated?,
  val androidSourceSets: List<AndroidSourceSet>?,
  val compilerPluginClasspathName: String,
  val variantFilter: VariantFilter,
) {
  // E.g. compileKotlin, compileKotlinJvm, compileDebugKotlin.
  private val taskSuffix = compileTaskProvider.name.substringAfter("compile")
  val stubsTaskName = "kaptGenerateStubs$taskSuffix"

  companion object {
    operator fun invoke(kotlinCompilation: KotlinCompilation<*>): Variant {
      // Sanity check.
      require(
        kotlinCompilation.platformType != androidJvm ||
          kotlinCompilation is KotlinJvmAndroidCompilation,
      ) {
        "The KotlinCompilation is KotlinJvmAndroidCompilation, but the platform type " +
          "is different."
      }

      val project = kotlinCompilation.target.project
      val extension = project.extensions.getByType(AnvilExtension::class.java)
      val androidVariant = (kotlinCompilation as? KotlinJvmAndroidCompilation)?.androidVariant

      val androidSourceSets = if (androidVariant != null) {
        val sourceSetsByName = project.extensions.getByType(BaseExtension::class.java)
          .sourceSets
          .associateBy { it.name }

        androidVariant.sourceSets.mapNotNull { sourceSetsByName[it.name] }
      } else {
        null
      }

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
        compileTaskProvider = kotlinCompilation.compileTaskProvider as
          TaskProvider<KotlinCompile>,
        androidVariant = androidVariant,
        androidSourceSets = androidSourceSets,
        compilerPluginClasspathName = PLUGIN_CLASSPATH_CONFIGURATION_NAME +
          kotlinCompilation.target.targetName.replaceFirstChar(Char::uppercase) +
          kotlinCompilation.name.replaceFirstChar(Char::uppercase),
        variantFilter = variantFilter,
      ).also {
        // Sanity check.
        check(it.compileTaskProvider.name.startsWith("compile"))
      }
    }
  }
}

private fun addPrefixToSourceSetName(
  prefix: String,
  sourceSetName: String,
): String = when (sourceSetName) {
  "main" -> prefix
  else -> "${prefix}${sourceSetName.capitalize()}"
}

internal fun KotlinCompilation<*>.kaptConfigName(): String {
  return addPrefixToSourceSetName("kapt", sourceSetName())
}

internal fun KotlinCompilation<*>.kaptConfigOrNull(project: Project): Configuration? =
  project.configurations.findByName(kaptConfigName())

internal fun KotlinCompilation<*>.kspConfigName(): String {
  return "${addPrefixToSourceSetName("ksp", sourceSetName())}KotlinProcessorClasspath"
}

internal fun KotlinCompilation<*>.kspConfigOrNull(project: Project): Configuration? =
  project.configurations.findByName(kspConfigName())

internal fun KotlinCompilation<*>.sourceSetName() =
  when (val comp = this@sourceSetName) {
    // The AGP source set names for test/androidTest variants
    // (e.g. the "androidTest" variant of the "debug" source set)
    // are concatenated differently than in the KGP and Java source sets.
    // In KGP and Java, we get `debugAndroidTest`, but in AGP we get `androidTestDebug`.
    // The KSP and KAPT configuration names are derived from the AGP name.
    is KotlinJvmAndroidCompilation ->
      comp.androidVariant.sourceSets
        // For ['debug', 'androidTest', 'debugAndroidTest'], the last name is always the one we want.
        .last()
        .name
    is KotlinJvmCompilation -> comp.name
    else -> name
  }

internal fun kspConfigurationNameForSourceSetName(sourceSetName: String): String {
  return when (sourceSetName) {
    "main" -> "ksp"
    else -> "ksp${sourceSetName.capitalize()}"
  }
}

internal fun KotlinTarget.kspConfigName(): String {
  return kspConfigurationNameForSourceSetName(targetName)
}

internal fun Configuration.hasDaggerCompilerDependency(): Boolean {
  return allDependencies.any { it.group == "com.google.dagger" && it.name == "dagger-compiler" }
}
