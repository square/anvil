package com.squareup.anvil.compiler.api

import org.jetbrains.kotlin.descriptors.ModuleDescriptor

/**
 * Contains context about the given Anvil compiler invocation. Considered a read-only API.
 *
 * These loosely correspond to the Anvil Gradle plugin's options, but we may add other information
 * in the future.
 */
public interface AnvilContext {
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
  public val generateFactories: Boolean

  /**
   * There are occasions where consumers of Anvil are only interested in generating Dagger
   * factories to speed up build times and don't want to make use of the other features. With this
   * flag Anvil will only generate Dagger factories and skip all other steps. If this flag is set
   * to `true`, then also [generateDaggerFactories] must be set to `true`.
   */
  public val generateFactoriesOnly: Boolean

  /**
   * List of absolute paths that anvil should generate Factory classes in.
   * When empty or ommited, Anvil applies no filtering and
   * generates factories in all available files.
   */
  public val generateFactoriesPathWhitelist: List<String>

  /**
   * Enabling this indicates that only code generation should run and no component merging should
   * run. This is useful for cases where you want to use `@ContributesTo`, `@ContributesBinding`,
   * or similar annotations but _not_ `@MergeComponent` or `@MergeSubcomponent` functionality.
   * This allows for anvil use in projects with kapt enabled but _not_ require disabling
   * incremental compilation in kapt stub generation tasks.
   */
  public val disableComponentMerging: Boolean

  /**
   * The module of the current compilation.
   */
  public val module: ModuleDescriptor
}
