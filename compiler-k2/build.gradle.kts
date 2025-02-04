plugins {
  id("conventions.library")
  id("conventions.publish")
  alias(libs.plugins.kotlin.kapt)
}

conventions {
  explicitApi = true
  kotlinCompilerArgs.addAll(
    // The flag is needed because we extend an interface that uses @JvmDefault and the Kotlin
    // compiler requires this flag when doing so.
    "-Xjvm-default=all",
    "-opt-in=org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi",
  )
}

publish {
  configurePom(
    artifactId = "compiler-k2",
    pomName = "Anvil Compiler Implementation for the K2 compiler",
    pomDescription = "The core implementation module for Anvil, responsible for hooking into " +
      "the Kotlin compiler and orchestrating code generation",
  )
}

dependencies {
  compileOnly(libs.auto.service.annotations)
  api(project(":annotations"))
  api(project(":compiler-k2-api"))
  api(project(":compiler-k2-utils"))
  api(libs.kotlin.compiler)
  api(libs.inject)
  api(libs.dagger2)

  kapt(libs.auto.service.processor)

  testCompileOnly(libs.auto.service.annotations)

  testImplementation(project(":compiler-testing"))
  testImplementation(libs.junit.jupiter.api)
  testImplementation(libs.kase)
  testImplementation(libs.kotest.assertions.api)
  testImplementation(libs.kotest.assertions.core.jvm)
}
