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
    pomName = "Anvil Compiler K2",
    pomDescription = "The core implementation module for Anvil, responsible for hooking into " +
      "the Kotlin compiler and orchestrating code generation",
  )
}

dependencies {
  implementation(libs.auto.service.annotations)
  implementation(libs.classgraph)
  implementation(project(":annotations"))
  implementation(project(":compiler-api"))
  implementation(project(":compiler-utils"))
  testImplementation(project(":compiler-testing"))
  implementation(platform(libs.kotlin.bom))
  implementation(libs.dagger2)
  implementation(libs.jsr250)
  implementation(libs.kotlinpoet)

  compileOnly(libs.auto.service.annotations)
  compileOnly(libs.kotlin.compiler.embeddable)

  kapt(libs.auto.service.processor)

  testImplementation(testFixtures(project(":compiler-utils")))
  testImplementation(libs.dagger2.compiler)
  // Force later guava version for Dagger's needs
  testImplementation(libs.guava)
  testImplementation(libs.kase)
  testImplementation(libs.kotest.assertions.core.jvm)
  testImplementation(libs.kotlin.annotation.processing.embeddable)
  testImplementation(libs.kotlin.compileTesting)
  testImplementation(libs.kotlin.compiler.embeddable)
  testImplementation(libs.kotlin.test)
  testImplementation(libs.kotlin.reflect)
  testImplementation(libs.truth)

  testRuntimeOnly(libs.kotest.assertions.core.jvm)
  testRuntimeOnly(libs.junit.vintage.engine)
  testRuntimeOnly(libs.junit.jupiter.engine)
}
