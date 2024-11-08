import com.rickbusarow.kgx.isInIdeaSync

plugins {
  id("conventions.library")
  id("conventions.publish")
  id("java-test-fixtures")
  alias(libs.plugins.kotlin.kapt)
  alias(libs.plugins.buildconfig)
}

val VERSION_NAME: String by project

buildConfig {
  className("BuildProperties")
  packageName("com.squareup.anvil.compiler.internal.testing")
  useKotlinOutput { topLevelConstants = true }

  buildConfigField("anvilVersion", VERSION_NAME)
}

dependencyGuard {
  configuration("testFixturesRuntimeClasspath")
}

conventions {

  kotlinCompilerArgs.add("-opt-in=org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi")
}

publish {
  configurePom(
    artifactId = "compiler-utils",
    pomName = "Anvil Compiler Utils",
    pomDescription =
    "Optional utility and extension functions for working with PSI and descriptors, " +
      "designed to simplify code generation tasks in Anvil",
  )
}

dependencies {
  api(project(":annotations"))
  api(project(":compiler-api"))
  api(libs.kotlin.compiler.embeddable)
  api(libs.kotlinpoet)

  testFixturesCompileOnly(libs.auto.service.annotations)

  kaptTestFixtures(libs.auto.service.processor)

  implementation(platform(libs.kotlin.bom))
  implementation(libs.dagger2)
  implementation(libs.inject)

  testFixturesApi(libs.classgraph)
  testFixturesApi(libs.kase)
  testFixturesApi(libs.kotlin.compileTesting) {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-annotation-processing-compiler")
  }
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
    compileOnly(libs.kotlin.compileTesting) {
      exclude(group = "org.jetbrains.kotlin", module = "kotlin-annotation-processing-compiler")
    }
    compileOnly(libs.truth)
  }
}
