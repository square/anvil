import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.ktlint)
  id("java-gradle-plugin")
}

gradlePlugin {
  plugins {
    register("gradleTests") {
      id = "conventions.gradle-tests"
      implementationClass = "com.squareup.anvil.conventions.GradleTestsPlugin"
    }
    register("library") {
      id = "conventions.library"
      implementationClass = "com.squareup.anvil.conventions.LibraryPlugin"
    }
    register("kotlinMultiplatformLibrary") {
      id = "conventions.kmp-library"
      implementationClass = "com.squareup.anvil.conventions.LibraryKmpPlugin"
    }
    register("minimalSupport") {
      id = "conventions.minimal"
      implementationClass = "com.squareup.anvil.conventions.MinimalSupportPlugin"
    }
    register("publish") {
      id = "conventions.publish"
      implementationClass = "com.squareup.anvil.conventions.PublishConventionPlugin"
    }
    register("root") {
      id = "conventions.root"
      implementationClass = "com.squareup.anvil.conventions.RootPlugin"
    }
  }
}

kotlin {
  jvmToolchain(libs.versions.jvm.toolchain.get().toInt())
  compilerOptions {
    jvmTarget.set(JvmTarget.fromTarget(libs.versions.jvm.target.minimal.get()))
  }
}
java.targetCompatibility = JavaVersion.toVersion(libs.versions.jvm.target.minimal.get())

ktlint {
  version = libs.versions.ktlint.get()
}

dependencies {
  compileOnly(gradleApi())

  api(libs.dropbox.dependencyGuard)
  api(libs.kotlinx.binaryCompatibility)
  api(libs.ktlintRaw)
  api(libs.kotlinpoet)
  api(libs.kgx)

  api(libs.kotlin.dokka)
  api(libs.kotlin.gradlePlugin)
  api(libs.mavenPublishRaw)

  // Expose the generated version catalog API to the plugins.
  implementation(files(libs::class.java.superclass.protectionDomain.codeSource.location))
}
