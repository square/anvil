plugins {
  alias(libs.plugins.agp.application) apply false
  alias(libs.plugins.agp.library) apply false
  alias(libs.plugins.gradlePublish) apply false
  alias(libs.plugins.kotlin.android) apply false
  alias(libs.plugins.kotlin.dokka) apply false
  alias(libs.plugins.kotlin.jvm) apply false
  alias(libs.plugins.kotlin.kapt) apply false
  alias(libs.plugins.kotlin.multiplatform) apply false
  alias(libs.plugins.mavenPublishBase) apply false
  id("conventions.root")
}
