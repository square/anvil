package com.squareup.anvil.plugin

open class AnvilExtension {
  /**
   * Highly experimental feature that allows you to use Anvil to generate Factory classes that
   * usually Dagger would generate for @Provides methods and @Inject constructors.
   *
   * The benefit of this feature is that you don't need to enable the Dagger compiler in this
   * module. That often means you can skip KAPT and the stub generating task. On top of it Anvil
   * generates Kotlin instead of Java code, which also allows Gradle to skip the Java compilation
   * task. The result are faster builds.
   *
   * This feature can only be enabled in Gradle modules that don't compile any Dagger component.
   * By default this feature is disabled.
   */
  var generateDaggerFactories = false
}
