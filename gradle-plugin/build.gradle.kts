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
    packageName = "com.squareup.anvil.plugin"
    useKotlinOutput {
      internalVisibility = true
      topLevelConstants = true
    }

    buildConfigField(
      type = "java.io.File",
      name = "localBuildM2Dir",
      value = rootProject.layout.buildDirectory.dir("m2").map { "File(\"${it}\")" },
    )

    buildConfigField("String", "anvilVersion", "\"$VERSION_NAME\"")
    buildConfigField("String", "kotlinVersion", "\"${libs.versions.kotlin.get()}\"")
    buildConfigField("String", "gradleVersion", "\"${gradle.gradleVersion}\"")
    buildConfigField("String", "daggerVersion", "\"${libs.versions.dagger.get()}\"")
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

  testImplementation(libs.junit)
  testImplementation(libs.truth)

  gradleTestImplementation(gradleTestKit())
  gradleTestImplementation(libs.junit)
  gradleTestImplementation(libs.junit5.engine)
  gradleTestImplementation(libs.junit5.jupiter)
  gradleTestImplementation(libs.junit5.jupiter.api)
  gradleTestImplementation(libs.kotlin.test)
  gradleTestImplementation(libs.truth)
}
