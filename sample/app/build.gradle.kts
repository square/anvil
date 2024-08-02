import com.android.builder.core.BuilderConstants

plugins {
  alias(libs.plugins.agp.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.ksp)
  id("dev.zacsweers.anvil")
  id("conventions.minimal")
}

anvil {
  useKsp(
    contributesAndFactoryGeneration = true,
    componentMerging = true,
  )
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
  implementation(project(":sample:library"))
  implementation(project(":sample:scopes"))

  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.core)
  implementation(libs.androidx.material)
  implementation(libs.dagger2)

  ksp(libs.dagger2.compiler)

  testImplementation(libs.junit)
  testImplementation(libs.truth)

  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.espresso.core)
  androidTestImplementation(libs.androidx.test.junit)
  androidTestImplementation(libs.androidx.test.rules)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.junit)
  androidTestImplementation(libs.truth)

  kspAndroidTest(libs.dagger2.compiler)
}
