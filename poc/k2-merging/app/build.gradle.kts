plugins {
  alias(libs.plugins.kotlin.jvm)
  id("com.squareup.anvil")
  id("conventions.minimal")
  id("org.jetbrains.kotlin.kapt")
}

dependencies {
  implementation(project(":library"))

  implementation(libs.dagger2)

  kapt(libs.dagger2.compiler)
}

pluginManager.withPlugin("kotlin-kapt") {
  kapt {
    correctErrorTypes = true
  }
}
