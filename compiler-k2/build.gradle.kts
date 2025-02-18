plugins {
  id("conventions.library")
  id("conventions.publish")
  alias(libs.plugins.kotlin.kapt)
}

conventions {
  explicitApi = true
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

  kapt(libs.auto.service.processor)

  testCompileOnly(libs.auto.service.annotations)

  testImplementation(project(":compiler-testing"))
  testImplementation(libs.junit.jupiter.api)
  testImplementation(libs.kase)
  testImplementation(libs.kotest.assertions.api)
  testImplementation(libs.kotest.assertions.core.jvm)
}
