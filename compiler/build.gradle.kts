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
  buildConfigField("boolean", "INCLUDE_KSP_TESTS", libs.versions.config.includeKspTests.get())
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
    artifactId = "compiler",
    pomName = "Anvil Compiler",
    pomDescription = "The core implementation module for Anvil, responsible for hooking into " +
      "the Kotlin compiler and orchestrating code generation",
  )
}

dependencies {

  // Force later guava version for Dagger's needs
  api(libs.dagger2.compiler)
  api(libs.kotlin.annotation.processing.embeddable)
  api(libs.kotlin.compiler.embeddable)
  api(libs.kotlin.metadata.jvm)
  api(libs.kotlin.scripting.compiler.embeddable)

  compileOnly(libs.auto.service.annotations)
  compileOnly(libs.kotlin.compiler.embeddable)
  compileOnly(libs.ksp.api)
  compileOnly(libs.ksp.compilerPlugin)

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
  testImplementation(libs.guava)
  testImplementation(libs.guava) { because("for Dagger") }
  testImplementation(libs.kase)
  testImplementation(libs.kotest.assertions.core.jvm)
  testImplementation(libs.kotlin.annotation.processing.embeddable)
  testImplementation(libs.kotlin.compileTesting)
  testImplementation(libs.kotlin.compileTesting.ksp)
  testImplementation(libs.kotlin.compiler.embeddable)
  testImplementation(libs.kotlin.reflect)
  testImplementation(libs.kotlin.test)
  testImplementation(libs.ksp.compilerPlugin)
  testImplementation(libs.truth)
  testImplementation(testFixtures(project(":compiler-utils")))

  testRuntimeOnly(libs.junit.jupiter.engine)
  testRuntimeOnly(libs.junit.vintage.engine)
  testRuntimeOnly(libs.kotest.assertions.core.jvm)

  // TODO rbusarow This is a hack to let me view the Kotlin source for unused plugins in the IDE,
  //  without needing to open the entire Kotlin project.
  run {
    check(System.getenv("CI") == null) { "delete me" }
    if (System.getProperty("idea.sync.active").toBoolean()) {
      compileOnly("org.jetbrains.kotlin:kotlin-parcelize-compiler:2.0.20")
    }
  }
}
