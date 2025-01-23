plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.kapt)
  id("com.squareup.anvil")
  id("conventions.minimal")
}

anvil {
  generateDaggerFactories = true
}

dependencies {
  anvil(project(":code-generator"))

  implementation(libs.dagger2)

  testImplementation(testFixtures("com.squareup.anvil:compiler-utils"))
  testImplementation(libs.junit)
  testImplementation(libs.truth)

  // Notice that Kapt is only enabled in tests for compiling our Dagger components. We also
  // generate a Dagger component in one of the code generators and this custom code generator
  // is triggered in tests.
  kaptTest(libs.dagger2.compiler)
}
