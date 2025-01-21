plugins {
  alias(libs.plugins.kotlin.jvm)
  id("com.squareup.anvil")
  id("conventions.minimal")
  // alias(libs.plugins.kotlin.kapt)
}

dependencies {
  implementation(project(":library"))

  implementation(libs.dagger2)
  testImplementation(libs.junit.jupiter.engine)
  testImplementation(libs.junit.jupiter.api)
  // kapt(libs.dagger2.compiler)
}

pluginManager.withPlugin("kotlin-kapt") {
  // kapt { correctErrorTypes = true }
}
