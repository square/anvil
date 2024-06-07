package com.squareup.anvil.plugin

import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.process.CommandLineArgumentProvider
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinSingleTargetExtension
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType.androidJvm
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType.jvm
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import javax.inject.Inject

public abstract class AnvilExtension @Inject constructor(
  private val project: Project,
  objects: ObjectFactory,
  private val providers: ProviderFactory,
) {

  init {
    val useKspBackend = providers.gradleProperty("com.squareup.anvil.useKspBackend")
      .map { it.toBoolean() }
      .getOrElse(false)
    val useKspComponentMergingBackend = providers.gradleProperty(
      "com.squareup.anvil.useKspComponentMergingBackend",
    )
      .map { it.toBoolean() }
      .getOrElse(false)
    if (useKspBackend || useKspComponentMergingBackend) {
      useKsp(useKspBackend, useKspComponentMergingBackend)
    }
  }

  /**
   * Allows you to use Anvil to generate Factory classes that usually the Dagger annotation
   * processor would generate for `@Provides` methods, `@Inject` constructors and `@Inject` fields.
   *
   * The benefit of this feature is that you don't need to enable the Dagger annotation processor
   * in this module. That often means you can skip KAPT and the stub generating task. In addition,
   * Anvil generates Kotlin instead of Java code, which allows Gradle to skip the Java compilation
   * task. The result is faster builds.
   *
   * This feature can only be enabled in Gradle modules that don't compile any Dagger component.
   * Since Anvil only processes Kotlin code, you shouldn't enable it in modules with mixed Kotlin /
   * Java sources either.
   *
   * This feature is disabled by default.
   *
   * This property can also be set via a Gradle property:
   *
   * ```properties
   * com.squareup.anvil.generateDaggerFactories=true
   * ```
   */
  public val generateDaggerFactories: Property<Boolean> = objects.property(Boolean::class.java)
    .conventionFromProperty("com.squareup.anvil.generateDaggerFactories", false)

  /**
   * There are occasions where consumers of Anvil are only interested in generating Dagger
   * factories to speed up build times and don't want to make use of the other features. With this
   * flag Anvil will only generate Dagger factories and skip all other steps. If this flag is set
   * to `true`, then also [generateDaggerFactories] must be set to `true`.
   *
   * This property can also be set via a Gradle property:
   *
   * ```properties
   * com.squareup.anvil.generateDaggerFactoriesOnly=true
   * ```
   */
  public val generateDaggerFactoriesOnly: Property<Boolean> = objects.property(Boolean::class.java)
    .conventionFromProperty("com.squareup.anvil.generateDaggerFactoriesOnly", false)

  /**
   * Enabling this indicates that only code generation should run and no component merging should
   * run. This is useful for cases where you want to use `@ContributesTo`, `@ContributesBinding`,
   * or similar annotations but _not_ `@MergeComponent` or `@MergeSubcomponent` functionality.
   * This allows for anvil use in projects with kapt enabled but _not_ require disabling
   * incremental compilation in kapt stub generation tasks.
   *
   * This property can also be set via a Gradle property:
   *
   * ```properties
   * com.squareup.anvil.disableComponentMerging=true
   * ```
   */
  public val disableComponentMerging: Property<Boolean> = objects.property(Boolean::class.java)
    .conventionFromProperty("com.squareup.anvil.disableComponentMerging", false)

  /**
   * Add Anvil generated source directories to sourceSets in Gradle for indexing visibility in the
   * IDE. This can be useful in debugging and is disabled by default.
   *
   * This property can also be set via a Gradle property:
   *
   * ```properties
   * com.squareup.anvil.syncGeneratedSources=true
   * ```
   */
  public val syncGeneratedSources: Property<Boolean> = objects.property(Boolean::class.java)
    .conventionFromProperty("com.squareup.anvil.syncGeneratedSources", false)

  /**
   * Add the `annotations-optional` artifact as a dependency to make annotations
   * like `@SingleIn` and `@ForScope` available to use. These are annotations
   * that are not strictly required but which we've found to be helpful with
   * managing larger dependency graphs.
   *
   * This property can also be set via a Gradle property:
   *
   * ```properties
   * com.squareup.anvil.addOptionalAnnotations=true
   * ```
   */
  public val addOptionalAnnotations: Property<Boolean> = objects.property(Boolean::class.java)
    .conventionFromProperty("com.squareup.anvil.addOptionalAnnotations", false)

  /**
   * Enables incremental compilation support.
   *
   * This is achieved by tracking the source files for each generated file,
   * which allows for two new behaviors:
   * - Generated code is "invalidated" and deleted when the source file is changed or deleted.
   * - Generated code is cached in a way that Gradle understands,
   *   and will be restored from cache along with other build artifacts.
   *
   * This feature is disabled by default.
   *
   * This property can also be set via a Gradle property:
   *
   * ```properties
   * com.squareup.anvil.trackSourceFiles=true
   * ```
   */
  public val trackSourceFiles: Property<Boolean> = objects.property(Boolean::class.java)
    .conventionFromProperty("com.squareup.anvil.trackSourceFiles", false)

  /**
   * @see useKsp
   */
  public val useKspBackend: Property<Boolean> = objects.property(Boolean::class.java)
    .convention(false)

  /**
   * @see useKsp
   */
  public val useKspComponentMergingBackend: Property<Boolean> = objects.property(
    Boolean::class.java,
  ).convention(false)

  /**
   * Enables the new [KSP](https://github.com/google/ksp) backends for Anvil. Note that this
   * requires the KSP plugin to already be on the buildscript classpath.
   *
   * These modes can optionally be enabled via these gradle properties:
   * ```properties
   * com.squareup.anvil.useKspBackend=true
   * com.squareup.anvil.useKspComponentMergingBackend=true
   * ```
   *
   * @param contributesAndFactoryGeneration This is an experimental feature that
   *   replaces the previous `AnalysisHandlerExtension`-based backend, which is removed in Kotlin
   *   2.0 and struggled with incremental compilation.
   * @param componentMerging This is an experimental feature that currently does nothing. It's a
   *   placeholder for future work. Requires [disableComponentMerging] to be `false`.
   */
  @JvmOverloads
  public fun useKsp(
    contributesAndFactoryGeneration: Boolean,
    componentMerging: Boolean = false,
  ) {
    useKspBackend.setDisallowChanges(contributesAndFactoryGeneration)
    useKspComponentMergingBackend.setDisallowChanges(componentMerging)
    // Wire KSP
    try {
      project.pluginManager.apply("com.google.devtools.ksp")
    } catch (e: Exception) {
      // KSP not on the classpath, ask them to add it
      error(
        "Anvil's KSP backends require the KSP plugin to be applied to the project. " +
          "Please apply the KSP Gradle plugin ('com.google.devtools.ksp') to your buildscript " +
          "and try again.",
      )
    }
    // Add the KSP dependency to the appropriate configurations
    // In KMP, we only add to androidJvm/jvm targets
    val kExtension = project.kotlinExtension
    if (kExtension is KotlinMultiplatformExtension) {
      kExtension.targets
        .matching { it.isSupportedType() }
        .configureEach {
          addKspDep(it.kspConfigName())
        }
    } else {
      addKspDep("ksp")
    }

    val willHaveDaggerFactories = generateDaggerFactories.map { anvilGenerated ->
      // If Anvil is creating factories due to `generateDaggerFactories`, then we're done.
      // Otherwise, we have to check if the Dagger compiler dependency is in KSP's classpath.
      anvilGenerated || kExtension.targets
        .filter { it.isSupportedType() }
        .any { target ->
          target.compilations.any { c ->
            // If using Anvil with KSP, Dagger factory generation can come from either KSP or KAPT.
            c.kspConfigOrNull(project)?.hasDaggerCompilerDependency() == true ||
              c.kaptConfigOrNull(project)?.hasDaggerCompilerDependency() == true
          }
        }
    }

    project.extensions.configure(KspExtension::class.java) { ksp ->
      // Do not convert this to a lambda.
      // It will leak the AnvilExtension instance and break configuration caching.
      ksp.arg(
        commandLineArgumentProvider(
          "generate-dagger-factories" to generateDaggerFactories,
          "generate-dagger-factories-only" to generateDaggerFactoriesOnly,
          "disable-component-merging" to disableComponentMerging,
          "will-have-dagger-factories" to willHaveDaggerFactories,
        ),
      )
    }
  }

  private fun addKspDep(configurationName: String) {
    project.dependencies.add(
      configurationName,
      "com.squareup.anvil:compiler:$VERSION",
    )
  }

  @Suppress("PropertyName")
  internal var _variantFilter: Action<VariantFilter>? = null

  /**
   * Configures each variant of this project. For Android projects these are the respective
   * Android variants, for JVM projects these are usually the main and test variant.
   */
  public fun variantFilter(action: Action<VariantFilter>) {
    _variantFilter = action
  }

  private fun Property<Boolean>.conventionFromProperty(
    name: String,
    default: Boolean,
  ): Property<Boolean> = convention(
    providers.gradleProperty(name)
      .map(String::toBoolean)
      .orElse(default),
  )

  private fun <T> Property<T>.setDisallowChanges(value: T?) {
    set(value)
    disallowChanges()
  }

  private companion object {
    val SUPPORTED_PLATFORMS = setOf(androidJvm, jvm)

    private fun KotlinTarget.isSupportedType(): Boolean = platformType in SUPPORTED_PLATFORMS
  }
}

/**
 * This function is propping up configuration caching in two ways:
 *
 * 1. It creates local references to the providers,
 *    so that we can pass them to the KSP task without a reference to `AnvilExtension`.
 * 2. It creates the `CommandLineArgumentProvider` lambda outside the `AnvilExtension` class,
 *    so that we can't accidentally capture `AnvilExtension` in the lambda.
 *
 * [AnvilExtension] currently isn't serializable for configuration caching
 * because of its `Project` property.
 */
private fun commandLineArgumentProvider(
  vararg args: Pair<String, Provider<Boolean>>,
): CommandLineArgumentProvider {
  return CommandLineArgumentProvider {
    args.map { (arg, provider) -> "$arg=${provider.get()}" }
  }
}

private val KotlinProjectExtension.targets: Iterable<KotlinTarget>
  get() = when (this) {
    is KotlinSingleTargetExtension<*> -> listOf(this.target)
    is KotlinMultiplatformExtension -> targets
    else -> error("Unexpected 'kotlin' extension $this")
  }
