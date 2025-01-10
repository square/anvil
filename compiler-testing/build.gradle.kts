plugins {
  id("conventions.library")
  id("conventions.publish")
  alias(libs.plugins.kotlin.kapt)
  alias(libs.plugins.buildconfig)
}

val VERSION_NAME: String by project

buildConfig {
  className("BuildProperties")
  packageName("com.squareup.anvil.compiler.testing")
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
    artifactId = "compiler-k2-testing",
    pomName = "Anvil Compiler K2 Testing",
    pomDescription = "Testing utilties for Anvil",
  )
}

dependencies {

  api(project(":annotations"))
  compileOnly(libs.auto.service.annotations)
  api(libs.classgraph)
  api(libs.dagger2)
  api(libs.dagger2.compiler)
  api(libs.guava)
  api(libs.jsr250)
  api(libs.kase)
  api(libs.kotest.assertions.core.jvm)
  api(libs.kotlin.annotation.processing.embeddable)
  api(libs.kotlin.compileTesting)
  api(libs.kotlin.compiler.embeddable)
  api(libs.kotlin.reflect)
  api(libs.kotlin.test)

  compileOnly(libs.auto.service.annotations)

  kapt(libs.auto.service.processor)

  testRuntimeOnly(libs.junit.jupiter.engine)
  testRuntimeOnly(libs.junit.vintage.engine)

  // api(project(":compiler-utils"))
  // api(testFixtures(project(":compiler-utils")))
}
