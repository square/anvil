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

  buildConfigField("FULL_TEST_RUN", libs.versions.config.fullTestRun.map { it.toBoolean() })
  buildConfigField("anvilVersion", VERSION_NAME)
  buildConfigField("kotlinVersion", libs.versions.kotlin)
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

  api(libs.classgraph)
  api(libs.dagger2)
  api(libs.dagger2.compiler)
  api(libs.guava)
  api(libs.jsr250)
  api(libs.kase)
  api(libs.kotest.assertions.core.jvm)
  api(libs.kotlin.annotation.processing.embeddable)
  api(libs.kotlin.compiler.embeddable)
  api(libs.kotlin.reflect)

  api(project(":annotations"))
  api(project(":compiler-k2-api"))

  compileOnly(libs.auto.service.annotations)

  kapt(libs.auto.service.processor)

  testRuntimeOnly(libs.junit.jupiter.engine)
}
