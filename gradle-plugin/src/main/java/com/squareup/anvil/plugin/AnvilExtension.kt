package com.squareup.anvil.plugin

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

public abstract class AnvilExtension @Inject constructor(objects: ObjectFactory) {
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
  public val generateDaggerFactories: Property<Boolean> = objects.property(Boolean::class.java)
    .convention(false)

  /**
   * There are occasions where consumers of Anvil are only interested in generating Dagger
   * factories to speed up build times and don't want to make use of the other features. With this
   * flag Anvil will only generate Dagger factories and skip all other steps. If this flag is set
   * to `true`, then also [generateDaggerFactories] must be set to `true`.
   */
  public val generateDaggerFactoriesOnly: Property<Boolean> = objects.property(Boolean::class.java)
    .convention(false)

  /**
   * Enabling this indicates that only code generation should run and no component merging should
   * run. This is useful for cases where you want to use `@ContributesTo`, `@ContributesBinding`,
   * or similar annotations but _not_ `@MergeComponent` or `@MergeSubcomponent` functionality.
   * This allows for anvil use in projects with kapt enabled but _not_ require disabling
   * incremental compilation in kapt stub generation tasks.
   */
  public val disableComponentMerging: Property<Boolean> = objects.property(Boolean::class.java)
    .convention(false)

  /**
   * Add Anvil generated source directories to sourceSets in Gradle for indexing visibility in the
   * IDE. This can be useful in debugging and is disabled by default.
   */
  public val syncGeneratedSources: Property<Boolean> = objects.property(Boolean::class.java)
    .convention(false)

  /**
   * Add the `annotations-optional` artifact as a dependency to make annotations like `@SingleIn` and `@ForScope`
   * available to use. These are annotations that are not strictly required but which we've found to be helpful with managing larger dependency graphs.
   */
  public val addOptionalAnnotations: Property<Boolean> = objects.property(Boolean::class.java)
    .convention(false)

  @Suppress("PropertyName")
  internal var _variantFilter: Action<VariantFilter>? = null

  /**
   * Configures each variant of this project. For Android projects these are the respective
   * Android variants, for JVM projects these are usually the main and test variant.
   */
  public fun variantFilter(action: Action<VariantFilter>) {
    _variantFilter = action
  }
}
