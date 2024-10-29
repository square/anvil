import com.rickbusarow.kgx.isInIdeaSync

plugins {
  id("conventions.library")
  id("conventions.publish")
  id("java-test-fixtures")
}

conventions {
  kotlinCompilerArgs.addAll(
    "-opt-in=org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi",
    "-opt-in=com.squareup.anvil.annotations.ExperimentalAnvilApi",
  )
}

publish {
  configurePom(
    artifactId = "compiler-utils",
    pomName = "Anvil Compiler Utils",
    pomDescription = "Optional utility and extension functions for working with PSI and descriptors, " +
      "designed to simplify code generation tasks in Anvil",
  )
}

dependencies {
  api(project(":annotations"))
  api(project(":compiler-api"))
  api(libs.kotlin.compiler)
  api(libs.ksp.api)
  api(libs.kotlinpoet.ksp)

  implementation(platform(libs.kotlin.bom))
  implementation(libs.dagger2)
  implementation(libs.inject)

  testFixturesApi(libs.kotlin.compileTesting)
  testFixturesApi(libs.kotlin.compileTesting.ksp)
  testFixturesImplementation(project(":compiler"))
  testFixturesImplementation(libs.dagger2.compiler)
  testFixturesImplementation(libs.dagger2)
  testFixturesImplementation(libs.junit)
  testFixturesImplementation(libs.truth)

  // Necessary because this is what dagger uses when it runs to support instantiating annotations at runtime
  testFixturesImplementation(libs.auto.value.annotations)
  testFixturesImplementation(libs.auto.value.processor)

  // This workaround is needed to resolve classes in the IDE properly.
  if (isInIdeaSync) {
    compileOnly(project(":compiler"))
    compileOnly(libs.dagger2.compiler)
    compileOnly(libs.junit)
    compileOnly(libs.kotlin.compileTesting)
    compileOnly(libs.kotlin.compileTesting.ksp)
    compileOnly(libs.truth)
  }
}
