plugins {
  alias(libs.plugins.gradlePublish)
  alias(libs.plugins.buildconfig)
  id("conventions.library")
  id("conventions.publish")
  id("conventions.gradle-tests")
  id("java-gradle-plugin")
}

val pomName = "Anvil Gradle Plugin"
val pomDescription = "A Kotlin compiler plugin to make dependency injection with Dagger 2 " +
  "easier by automatically merging Dagger modules and component interfaces."

conventions {

  // Make sure that the BuildProperties files are generated during an IDE sync,
  // so that references to them will be resolved.
  addTasksToIdeSync(
    "generateBuildConfig",
    "generateGradleTestBuildConfig",
  )
}
publish {
  configurePom(
    artifactId = "gradle-plugin",
    pomName = pomName,
    pomDescription = pomDescription,
  )
}

val GROUP: String by project
val VERSION_NAME: String by project

buildConfig {
  className("BuildProperties")
  packageName("com.squareup.anvil.plugin")
  useKotlinOutput {
    internalVisibility = true
    topLevelConstants = true
  }

  buildConfigField("String", "GROUP", "\"$GROUP\"")
  buildConfigField("String", "VERSION", "\"$VERSION_NAME\"")

  sourceSets.named("gradleTest") {
    className = "BuildProperties"
    packageName = "com.squareup.anvil.plugin.buildProperties"
    useKotlinOutput {
      internalVisibility = true
      topLevelConstants = true
    }

    val buildM2 = rootProject.layout.buildDirectory.dir("m2").map { "File(\"${it}\")" }
    buildConfigField("java.io.File", "localBuildM2Dir", buildM2)
    buildConfigField("String", "anvilVersion", "\"$VERSION_NAME\"")
    buildConfigField("String", "kotlinVersion", "\"${libs.versions.kotlin.get()}\"")
    buildConfigField("String", "gradleVersion", "\"${gradle.gradleVersion}\"")
    buildConfigField("String", "daggerVersion", "\"${libs.versions.dagger.get()}\"")
    buildConfigField("kotlin.Boolean", "fullTestRun", libs.versions.config.fullTestRun.get())
  }
}

gradlePlugin {
  website = project.findProperty("POM_URL") as String
  vcsUrl = project.findProperty("POM_SCM_URL") as String

  plugins {
    register("anvilPlugin") {
      id = "com.squareup.anvil"
      displayName = pomName
      implementationClass = "com.squareup.anvil.plugin.AnvilPlugin"
      description = pomDescription
      tags.addAll("dagger2", "dagger2-android", "kotlin", "kotlin-compiler-plugin")
    }
  }
}

dependencies {
  // Necessary to bump a transitive dependency.
  compileOnly(libs.kotlin.reflect)

  // Compile only so we don"t preempt what the consuming project actually uses
  compileOnly(libs.kotlin.gradlePlugin)
  compileOnly(libs.kotlin.gradlePluginApi)
  compileOnly(libs.agp)
  compileOnly(libs.ksp.gradlePlugin)

  testImplementation(libs.junit)
  testImplementation(libs.truth)

  gradleTestImplementation(gradleTestKit())
  gradleTestImplementation(libs.junit.jupiter)
  gradleTestImplementation(libs.junit.jupiter.api)
  gradleTestImplementation(libs.junit.jupiter.engine)
  gradleTestImplementation(libs.kase)
  gradleTestImplementation(libs.kase.gradle)
  gradleTestImplementation(libs.kase.gradle.dsl)
  gradleTestImplementation(libs.kotest.assertions.api)
  gradleTestImplementation(libs.kotest.assertions.core.jvm)
  gradleTestImplementation(libs.kotlin.test)
  gradleTestImplementation(libs.truth)
}
