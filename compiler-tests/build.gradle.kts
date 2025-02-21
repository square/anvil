plugins {
  id("conventions.library")
  alias(libs.plugins.buildconfig)
}

buildConfig {
  className("BuildProperties")
  packageName("com.squareup.anvil.compiler.tests")
  useKotlinOutput { topLevelConstants = true }

  buildConfigField("boolean", "FULL_TEST_RUN", libs.versions.config.fullTestRun.get())
}

dependencies {

  testImplementation(libs.dagger2)
  testImplementation(libs.dagger2.compiler)
  testImplementation(libs.guava)
  testImplementation(libs.jsr250)
  testImplementation(libs.kase)
  testImplementation(libs.kotest.assertions.core.jvm)
  testImplementation(libs.kotlin.annotation.processing.embeddable)
  testImplementation(libs.kotlin.compiler)
  testImplementation(libs.kotlin.reflect)
  testImplementation(libs.kotlin.test)
  testImplementation(libs.kotlinpoet)
  testImplementation(libs.truth)
  testImplementation(platform(libs.kotlin.bom))
  testImplementation(project(":annotations"))
  testImplementation(project(":compiler"))
  testImplementation(project(":compiler-api"))
  testImplementation(project(":compiler-k2"))
  testImplementation(project(":compiler-testing"))
  testImplementation(project(":compiler-utils"))

  testRuntimeOnly(libs.junit.jupiter.engine)
  testImplementation(libs.junit.jupiter.api)
  testRuntimeOnly(libs.kotest.assertions.core.jvm)
}
