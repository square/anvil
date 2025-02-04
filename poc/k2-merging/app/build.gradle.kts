plugins {
  alias(libs.plugins.kotlin.jvm)
  id("com.squareup.anvil")
  id("conventions.minimal")
  id("com.sergei-lapin.napt") version("1.19") apply true
  // id("java-anno")
  // alias(libs.plugins.java.kapt)
}

dependencies {
  implementation(project(":library"))

  implementation(libs.dagger2)
  testImplementation(libs.junit.jupiter.engine)
  testImplementation(libs.junit.jupiter.api)
  annotationProcessor(libs.dagger2.compiler)
}

// pluginManager.withPlugin("kotlin-kapt") {
//   kapt { correctErrorTypes = true }
// }
