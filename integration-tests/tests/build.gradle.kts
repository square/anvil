plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.kapt)
  id("dev.zacsweers.anvil")
  id("conventions.minimal")
}

kotlin {
  explicitApi()
}

anvil {
  variantFilter {
    ignore = name == "main"
  }
}

dependencies {
  testImplementation(project(":integration-tests:library"))
  testImplementation(testFixtures("dev.zacsweers.anvil:compiler-utils"))
  testImplementation(libs.dagger2)
  testImplementation(libs.junit)
  testImplementation(libs.truth)

  kaptTest(libs.dagger2.compiler)
}
