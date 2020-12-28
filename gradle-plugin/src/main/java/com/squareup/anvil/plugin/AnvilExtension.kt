package com.squareup.anvil.plugin

open class AnvilExtension {
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
  var generateDaggerFactories = false

  /**
   * There are occasions where consumers of Anvil are only interested in generating Dagger
   * factories to speed up build times and don't want to make use of the other features. With this
   * flag Anvil will only generate Dagger factories and skip all other steps. If this flag is set
   * to `true`, then also [generateDaggerFactories] must be set to `true`.
   */
  var generateDaggerFactoriesOnly = false
}
