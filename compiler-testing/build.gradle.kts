plugins {
  id("conventions.library")
  id("conventions.publish")
  alias(libs.plugins.kotlin.kapt)
  alias(libs.plugins.buildconfig)
}

conventions {
  kotlinCompilerArgs.addAll(
    // The flag is needed because we extend an interface that uses @JvmDefault and the Kotlin
    // compiler requires this flag when doing so.
    "-Xjvm-default=all",
    "-opt-in=org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi",
  )
  addTasksToIdeSync("generateBuildConfig")
}

publish {
  configurePom(
    artifactId = "compiler-testing",
    pomName = "Anvil Compiler Testing",
    pomDescription = "Testing utilties for Anvil",
  )
}

val VERSION_NAME: String by project

buildConfig {
  className("BuildConfig")
  packageName("com.squareup.anvil.compiler.testing")
  useKotlinOutput { topLevelConstants = false }

  buildConfigField("FULL_TEST_RUN", libs.versions.config.fullTestRun.map { it.toBoolean() })
  buildConfigField("anvilVersion", VERSION_NAME)
  buildConfigField("kotlinVersion", libs.versions.kotlin)
  buildConfigField(
    "org.jetbrains.kotlin.config.LanguageVersion",
    "languageVersion",
    libs.versions.kotlinLanguageVersion.map { "LanguageVersion.KOTLIN_${it.replace(".", "_")}" },
  )
}

dependencies {

  api(libs.classgraph)
  api(libs.dagger2)
  api(libs.dagger2.compiler)
  api(libs.guava)
  api(libs.jsr250)
  api(libs.kase)
  api(libs.kotest.assertions.core.jvm)
  api(libs.kotlin.annotation.processing.embeddable)
  api(libs.kotlin.compiler.embeddable)
  api(libs.kotlin.reflect)

  api(project(":annotations"))
  api(project(":compiler-k2-api"))
  api(project(":compiler-k2-utils"))

  compileOnly(libs.auto.service.annotations)

  kapt(libs.auto.service.processor)

  testRuntimeOnly(libs.junit.jupiter.engine)
}
