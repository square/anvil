import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
  id("conventions.kmp-library")
  id("conventions.publish")
}

publish {
  configurePom(
    artifactId = "annotations-optional",
    pomName = "Anvil Optional Annotations",
    pomDescription = "Optional annotations that we\"ve found to be helpful with managing larger dependency graphs",
    overrideArtifactId = false,
  )
}

kotlin {
  @OptIn(ExperimentalKotlinGradlePluginApi::class)
  compilerOptions {
    freeCompilerArgs.add("-Xexpect-actual-classes")
  }
}

dependencies {
  commonMainCompileOnly(libs.kotlinInject)
  // non jvm targets don't support compile only dependencies
  nonJvmMainApi(libs.kotlinInject)

  jvmMainCompileOnly(libs.inject)
  jvmMainCompileOnly(libs.jakarta)
}
