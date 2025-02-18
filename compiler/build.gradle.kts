plugins {
  id("conventions.library")
  id("conventions.publish")
  alias(libs.plugins.kotlin.kapt)
  alias(libs.plugins.buildconfig)
}

buildConfig {
  className("BuildProperties")
  packageName("com.squareup.anvil.compiler")
  useKotlinOutput { topLevelConstants = true }

  buildConfigField("boolean", "FULL_TEST_RUN", libs.versions.config.fullTestRun.get())
}

conventions {
  kotlinCompilerArgs.add("-opt-in=org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi")
  addTasksToIdeSync("generateBuildConfig")
}

publish {
  configurePom(
    artifactId = "compiler",
    pomName = "Anvil Compiler",
    pomDescription = "The core implementation module for Anvil, responsible for hooking into " +
      "the Kotlin compiler and orchestrating code generation",
  )
}

dependencies {
  implementation(project(":annotations"))
  implementation(project(":compiler-api"))
  implementation(project(":compiler-utils"))
  implementation(platform(libs.kotlin.bom))
  implementation(libs.dagger2)
  implementation(libs.jsr250)
  implementation(libs.kotlinpoet)

  compileOnly(libs.auto.service.annotations)
  compileOnly(libs.kotlin.compiler)

  kapt(libs.auto.service.processor)

  testImplementation(project(":compiler-testing"))
  testImplementation(testFixtures(project(":compiler-utils")))
  testImplementation(libs.dagger2.compiler)
  // Force later guava version for Dagger's needs
  testImplementation(libs.guava)
  testImplementation(libs.kase)
  testImplementation(libs.kotest.assertions.core.jvm)
  testImplementation(libs.kotlin.annotation.processing.embeddable)
  testImplementation(libs.kotlin.compileTesting)
  testImplementation(libs.kotlin.compiler)
  testImplementation(libs.kotlin.test)
  testImplementation(libs.kotlin.reflect)
  testImplementation(libs.truth)

  testRuntimeOnly(libs.kotest.assertions.core.jvm)
  testRuntimeOnly(libs.junit.jupiter.engine)
}
