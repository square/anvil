package com.squareup.anvil.plugin

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject
import kotlin.DeprecationLevel.HIDDEN

abstract class AnvilExtension @Inject constructor(objects: ObjectFactory) {
  /**
   * Allows you to use Anvil to generate Factory classes that usually the Dagger annotation
   * processor would generate for @Provides methods, @Inject constructors and @Inject fields.
   *
   * The benefit of this feature is that you don't need to enable the Dagger annotation processor
   * in this module. That often means you can skip KAPT and the stub generating task. In addition
   * Anvil generates Kotlin instead of Java code, which allows Gradle to skip the Java compilation
   * task. The result is faster builds.
   *
   * This feature can only be enabled in Gradle modules that don't compile any Dagger component.
   * Since Anvil only processes Kotlin code, you shouldn't enable it in modules with mixed Kotlin /
   * Java sources either.
   *
   * By default this feature is disabled.
   */
  val generateDaggerFactories: Property<Boolean> = objects.property(Boolean::class.java)
    .convention(false)

  /**
   * There are occasions where consumers of Anvil are only interested in generating Dagger
   * factories to speed up build times and don't want to make use of the other features. With this
   * flag Anvil will only generate Dagger factories and skip all other steps. If this flag is set
   * to `true`, then also [generateDaggerFactories] must be set to `true`.
   */
  val generateDaggerFactoriesOnly: Property<Boolean> = objects.property(Boolean::class.java)
    .convention(false)

  /**
   * Enabling this indicates that only code generation should run and no component merging should
   * run. This is useful for cases where you want to use `@ContributesTo`, `@ContributesBinding`,
   * or similar annotations but _not_ `@MergeComponent` or `@MergeSubcomponent` functionality.
   * This allows for anvil use in projects with kapt enabled but _not_ require disabling
   * incremental compilation in kapt stub generation tasks.
   */
  val disableComponentMerging: Property<Boolean> = objects.property(Boolean::class.java)
    .convention(false)

  /**
   * Add Anvil generated source directories to sourceSets in Gradle for indexing visibility. This
   * can be useful in debugging and is enabled by default but can be disabled if you don't
   * need/want this.
   */
  val addGeneratedDirToSourceSets: Property<Boolean> = objects.property(Boolean::class.java)
    .convention(true)

  /*
   * The below properties are legacy former properties. We do a bit of Kotlin sugar to preserve
   * binary compatibility but not Kotlin source compatibility.
   *
   * @get:JvmName("getGenerateDaggerFactories") preserves the get signature with the old name. Even
   * though this has the same name as the new property, it returns a Boolean instead of a Property.
   * This is not legal in source but it _is_ legal in the JVM at runtime, so they are
   * binary-compatible.
   *
   * @set:JvmName("setGenerateDaggerFactories") preserves the set signature with the old name. Since
   * there is no setter for the new property, this has no overload ambiguity. We do hide it anyway
   * with JvmSynthetic just to keep the API lean and simple.
   *
   * @JvmSynthetic hides this from non-Kotlin callers to avoid overload ambiguity.
   *
   * DeprecationLevel.HIDDEN further hides this from Kotlin users (since it's a source-breaking
   * change anyway). Kotlin previously compiled against this will still work at runtime, but new
   * sources will now point to the new property and require using `.set()` and `.get()`
   */

  @get:[JvmName("getGenerateDaggerFactories") JvmSynthetic]
  @set:[JvmName("setGenerateDaggerFactories") JvmSynthetic]
  var generateDaggerFactoriesLegacy: Boolean
    @Deprecated(
      "Use generateDaggerFactories property",
      ReplaceWith("generateDaggerFactories.get()"),
      level = HIDDEN
    )
    get() = generateDaggerFactories.get()
    @Deprecated(
      "Use generateDaggerFactories property",
      ReplaceWith("generateDaggerFactories.set(value)"),
      level = HIDDEN
    )
    set(value) = generateDaggerFactories.set(value)

  @get:[JvmName("getDaggerFactoriesOnly") JvmSynthetic]
  @set:[JvmName("setDaggerFactoriesOnly") JvmSynthetic]
  var generateDaggerFactoriesOnlyLegacy: Boolean
    @Deprecated(
      "Use generateDaggerFactoriesOnly property",
      ReplaceWith("generateDaggerFactoriesOnly.get()"),
      level = HIDDEN
    )
    get() = generateDaggerFactoriesOnly.get()
    @Deprecated(
      "Use generateDaggerFactoriesOnly property",
      ReplaceWith("generateDaggerFactoriesOnly.set(value)"),
      level = HIDDEN
    )
    set(value) = generateDaggerFactoriesOnly.set(value)

  @get:[JvmName("getDisableComponentMerging") JvmSynthetic]
  @set:[JvmName("setDisableComponentMerging") JvmSynthetic]
  var disableComponentMergingLegacy: Boolean
    @Deprecated(
      "Use disableComponentMerging property",
      ReplaceWith("disableComponentMerging.get()"),
      level = HIDDEN
    )
    get() = disableComponentMerging.get()
    @Deprecated(
      "Use disableComponentMerging property",
      ReplaceWith("disableComponentMerging.set(value)"),
      level = HIDDEN
    )
    set(value) = disableComponentMerging.set(value)
}
