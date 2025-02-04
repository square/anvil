plugins {
  alias(libs.plugins.kotlin.jvm)
  id("com.squareup.anvil")
  id("conventions.minimal")
}

if (true) {
  anvil {
    generateDaggerFactories = true
  }
} else {
  apply(plugin = "org.jetbrains.kotlin.kapt")

  dependencies {
    "kapt"(libs.dagger2.compiler)
  }
}

dependencies {
  api(libs.dagger2)
}
