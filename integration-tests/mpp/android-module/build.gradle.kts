import com.android.builder.core.BuilderConstants
import com.squareup.anvil.plugin.AndroidVariantFilter

plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.kapt)
  alias(libs.plugins.agp.library)
  id("com.squareup.anvil")
  id("conventions.minimal")
}

android {
  compileSdk = 33
  namespace = "com.squareup.anvil.mpp"

  defaultConfig {
    minSdk = 24
    @Suppress("UnstableApiUsage")
    targetSdk = 33
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

kotlin {

  androidTarget()

  sourceSets {
    val androidMain by getting {
      dependencies {

        implementation(libs.dagger2)
      }
    }

    val androidUnitTest by getting {
      dependencies {
        implementation(libs.junit)
        implementation(libs.truth)
      }
    }

    val androidInstrumentedTest by getting {
      dependencies {

        implementation(libs.junit)
        implementation(libs.truth)
      }
    }
  }
}

anvil {
  variantFilter filter@{
    @Suppress("DEPRECATION")
    ignore = (this@filter as? AndroidVariantFilter)
      ?.androidVariant is com.android.build.gradle.api.UnitTestVariant
  }
}

dependencies {

  val kapt by configurations.getting
  kapt.dependencies.addLater(libs.dagger2.compiler)

  // This dependency isn't needed. It's only here for testing purposes (this is still an
  // integration test).
  anvilAndroidTest(project(":code-generator"))
}
