plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.kapt)
  id("com.squareup.anvil")
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
  testImplementation(project(":library"))
  testImplementation(testFixtures("com.squareup.anvil:compiler-utils"))
  testImplementation(libs.dagger2)
  testImplementation(libs.junit)
  testImplementation(libs.truth)

  kaptTest(libs.dagger2.compiler)
}
