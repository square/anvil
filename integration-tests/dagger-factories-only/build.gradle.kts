import com.squareup.anvil.plugin.AnvilExtension

plugins {
  alias(libs.plugins.kotlin.jvm)
  id("conventions.minimal")
}

if (libs.versions.config.generateDaggerFactoriesWithAnvil.get().toBoolean()) {
  apply(plugin = "dev.zacsweers.anvil")

  configure<AnvilExtension> {
    generateDaggerFactories = true
    generateDaggerFactoriesOnly = true
  }
} else {
  apply(plugin = "org.jetbrains.kotlin.kapt")

  dependencies {
    "kapt"(libs.dagger2.compiler)
    "kaptTest"(libs.dagger2.compiler)
  }
}

dependencies {
  implementation(libs.dagger2)
  testImplementation(libs.junit)
  testImplementation(libs.kotlin.test)
  testImplementation(libs.truth)
}
