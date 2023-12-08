plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.kapt)
  id("com.squareup.anvil")
  id("conventions.minimal")
}

kotlin {
  jvm {
    withJava()
  }

  sourceSets {

    val jvmMain by getting {
      dependencies {
        val kapt by configurations.getting
        kapt.dependencies.addLater(libs.dagger2.compiler)

        implementation(libs.dagger2)
      }
    }
    val jvmTest by getting {
      dependencies {
        implementation(libs.junit)
        implementation(libs.truth)
      }
    }
  }
}
