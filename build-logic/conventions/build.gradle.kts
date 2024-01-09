import com.rickbusarow.kgx.fromInt
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

buildscript {
  dependencies {
    classpath(libs.kgx)
  }
}

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
    jvmTarget.set(JvmTarget.fromInt(libs.versions.jvm.target.minimal.get().toInt()))
  }
}
tasks.withType(JavaCompile::class.java).configureEach {
  options.release.set(libs.versions.jvm.target.minimal.get().toInt())
}

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
