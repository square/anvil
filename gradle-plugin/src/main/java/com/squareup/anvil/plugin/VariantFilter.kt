package com.squareup.anvil.plugin

import org.gradle.api.Named

public interface VariantFilter : Named {

  /**
   * Indicate whether or not to ignore Anvil for this particular variant. Default is false.
   */
  public var ignore: Boolean

  /**
   * Indicate whether Dagger factory generation for this variant should be enabled. The default
   * value comes from the [AnvilExtension].
   *
   * @see AnvilExtension.generateDaggerFactories for more details.
   */
  public var generateDaggerFactories: Boolean

  /**
   * Indicate whether only Dagger factories for this variant should be generated. The default
   * value comes from the [AnvilExtension].
   *
   * @see AnvilExtension.generateDaggerFactoriesOnly for more details.
   */
  public var generateDaggerFactoriesOnly: Boolean

  /**
   * Indicate whether component merging for this variant should be disabled. The default
   * value comes from the [AnvilExtension].
   *
   * @see AnvilExtension.disableComponentMerging for more details.
   */
  public var disableComponentMerging: Boolean

  /**
   * Add Anvil generated source directories to sourceSets in Gradle for indexing visibility in the
   * IDE. This can be useful in debugging and is disabled by default.
   *
   * @see AnvilExtension.syncGeneratedSources for more details.
   */
  public var syncGeneratedSources: Boolean

  /**
   * Add the `annotations-optional` artifact as a dependency. The default
   * value comes from the [AnvilExtension].
   *
   * @see AnvilExtension.addOptionalAnnotations for more details.
   */
  public var addOptionalAnnotations: Boolean

  /**
   * Enables incremental compilation support for this variant.
   * The default value comes from the [AnvilExtension].
   *
   * @see AnvilExtension.trackSourceFiles for more details.
   */
  public var trackSourceFiles: Boolean
}

internal class CommonFilter(
  private val name: String,
  private val extension: AnvilExtension,
) : VariantFilter {
  override fun getName(): String = name
  override var ignore: Boolean = false

  private var generateDaggerFactoriesOverride: Boolean? = null
  override var generateDaggerFactories: Boolean
    get() = generateDaggerFactoriesOverride ?: extension.generateDaggerFactories.get()
    set(value) {
      generateDaggerFactoriesOverride = value
    }

  private var generateDaggerFactoriesOnlyOverride: Boolean? = null
  override var generateDaggerFactoriesOnly: Boolean
    get() = generateDaggerFactoriesOnlyOverride ?: extension.generateDaggerFactoriesOnly.get()
    set(value) {
      generateDaggerFactoriesOnlyOverride = value
    }

  private var disableComponentMergingOverride: Boolean? = null
  override var disableComponentMerging: Boolean
    get() = disableComponentMergingOverride ?: extension.disableComponentMerging.get()
    set(value) {
      disableComponentMergingOverride = value
    }

  private var syncGeneratedSourcesOverride: Boolean? = null
  override var syncGeneratedSources: Boolean
    get() = syncGeneratedSourcesOverride ?: extension.syncGeneratedSources.get()
    set(value) {
      syncGeneratedSourcesOverride = value
    }

  private var addOptionalAnnotationsOverride: Boolean? = null
  override var addOptionalAnnotations: Boolean
    get() = addOptionalAnnotationsOverride ?: extension.addOptionalAnnotations.get()
    set(value) {
      addOptionalAnnotationsOverride = value
    }

  private var trackSourceFilesOverride: Boolean? = null
  override var trackSourceFiles: Boolean
    get() = trackSourceFilesOverride ?: extension.trackSourceFiles.get()
    set(value) {
      trackSourceFilesOverride = value
    }
}

public class JvmVariantFilter internal constructor(
  commonFilter: CommonFilter,
) : VariantFilter by commonFilter

public class AndroidVariantFilter internal constructor(
  commonFilter: CommonFilter,
  @Suppress("TYPEALIAS_EXPANSION_DEPRECATION")
  public val androidVariant: BaseVariantDeprecated,
) : VariantFilter by commonFilter
