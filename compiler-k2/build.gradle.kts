plugins {
  id("conventions.library")
  id("conventions.publish")
  alias(libs.plugins.kotlin.kapt)
  alias(libs.plugins.buildconfig)
}

val VERSION_NAME: String by project

buildConfig {
  className("BuildProperties")
  packageName("com.squareup.anvil.compiler.k2")
  useKotlinOutput { topLevelConstants = true }

  buildConfigField("boolean", "FULL_TEST_RUN", libs.versions.config.fullTestRun.get())
  buildConfigField("anvilVersion", VERSION_NAME)
}

conventions {
  kotlinCompilerArgs.addAll(
    // The flag is needed because we extend an interface that uses @JvmDefault and the Kotlin
    // compiler requires this flag when doing so.
    "-Xjvm-default=all",
    "-opt-in=org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi",
  )
  addTasksToIdeSync("generateBuildConfig")
}

publish {
  configurePom(
    artifactId = "compiler-k2",
    pomName = "Anvil Compiler for K2",
    pomDescription = "The core implementation module for Anvil, responsible for hooking into " +
      "the Kotlin compiler and orchestrating code generation",
  )
}

dependencies {

  api(libs.dagger2.compiler)
  api(libs.kotlin.annotation.processing.embeddable)
  api(libs.kotlin.compiler.embeddable)
  api(libs.kotlin.metadata.jvm)
  api(libs.kotlin.scripting.compiler.embeddable)

  implementation(libs.auto.service.annotations)
  implementation(libs.classgraph)
  implementation(libs.dagger2)
  implementation(libs.jakarta.inject)
  implementation(libs.jsr250)
  implementation(libs.kotlinpoet)
  implementation(libs.kotlinpoet.ksp)
  implementation(platform(libs.kotlin.bom))
  implementation(project(":annotations"))
  implementation(project(":compiler-api"))
  implementation(project(":compiler-utils"))

  kapt(libs.auto.service.processor)

  testImplementation(libs.dagger2.compiler)
  testImplementation(libs.guava) { because("for Dagger") }
  testImplementation(libs.kase)
  testImplementation(libs.kotest.assertions.core.jvm)
  testImplementation(libs.kotlin.compileTesting)
  testImplementation(libs.kotlin.compileTesting.ksp)
  testImplementation(libs.kotlin.reflect)
  testImplementation(libs.kotlin.test)
  testImplementation(libs.ksp.compilerPlugin)
  testImplementation(libs.truth)
  testImplementation(testFixtures(project(":compiler-utils")))

  testRuntimeOnly(libs.junit.jupiter.engine)
  testRuntimeOnly(libs.junit.vintage.engine)
  testRuntimeOnly(libs.kotest.assertions.core.jvm)
}
