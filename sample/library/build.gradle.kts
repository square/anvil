plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.ksp)
  id("com.squareup.anvil")
  id("conventions.minimal")
}

if (libs.versions.config.generateDaggerFactoriesWithAnvil.get().toBoolean()) {
  anvil {
    generateDaggerFactories = true
    disableComponentMerging = true
    useKsp(
      contributesAndFactoryGeneration = true,
      componentMerging = false,
    )
  }
} else {
  apply(plugin = "org.jetbrains.kotlin.kapt")

  dependencies {
    "kapt"(libs.dagger2.compiler)
  }
}

dependencies {
  api(project(":sample:scopes"))
  api(libs.dagger2)
}
