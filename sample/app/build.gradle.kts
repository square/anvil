import com.android.builder.core.BuilderConstants

plugins {
  alias(libs.plugins.agp.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.kapt)
  id("com.squareup.anvil")
  id("conventions.minimal")
}

android {
  compileSdk = 33
  namespace = "com.squareup.anvil.sample"

  defaultConfig {
    applicationId = "com.squareup.anvil.sample"
    minSdk = 24
    targetSdk = 33
    versionCode = 1
    versionName = "1.0.0"

    testInstrumentationRunner = "com.squareup.anvil.sample.TestRunner"
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  lint {
    @Suppress("UnstableApiUsage")
    warningsAsErrors = true
  }
}

androidComponents {
  beforeVariants { variant ->
    variant.enable = variant.buildType != BuilderConstants.RELEASE
  }
}

dependencies {
  implementation(project(":library"))
  implementation(project(":scopes"))

  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.core)
  implementation(libs.androidx.material)
  implementation(libs.dagger2)

  kapt(libs.dagger2.compiler)

  testImplementation(libs.junit)
  testImplementation(libs.truth)

  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.espresso.core)
  androidTestImplementation(libs.androidx.test.junit)
  androidTestImplementation(libs.androidx.test.rules)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.junit)
  androidTestImplementation(libs.truth)

  kaptAndroidTest(libs.dagger2.compiler)
}

// Keep that here for testing purposes. The Anvil Gradle plugin will set this flag to false again,
// otherwise Android projects will fail to build. This serves kinda as an integration test.
pluginManager.withPlugin("kotlin-kapt") {
  kapt {
    correctErrorTypes = true
  }
}
