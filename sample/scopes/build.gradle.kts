plugins {
  alias(libs.plugins.kotlin.jvm)
  id("conventions.minimal")
}

dependencies {
  api(libs.inject)
}
