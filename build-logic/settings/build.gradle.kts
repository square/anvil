plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.ktlint)
  alias(libs.plugins.buildParameters)
  id("java-gradle-plugin")
}

kotlin {
  jvmToolchain {
    languageVersion.set(JavaLanguageVersion.of(11))
  }
}

ktlint {
  version = libs.versions.ktlint.get()
}

gradlePlugin {
  plugins {
    register("settingsPlugin") {
      id = "com.squareup.anvil.gradle-settings"
      implementationClass = "com.squareup.anvil.builds.settings.SettingsPlugin"
    }
  }
}

buildParameters {

  bool("inCi") {
    description.set("Looks for the CI environment variable")
    defaultValue.set(false)
    environmentVariableName.set("CI")
  }

  group("checks") {

    bool("fullTestRun") {
      description.set("Run all tests, including the slow ones")
      defaultValue.set(true)
    }

    bool("warningsAsErrors") {
      description.set("Run all tests, including the slow ones")
      defaultValue.set(true)
    }

    group("include") {

      bool("ksp") {
        description.set("Test KSP generation")
        defaultValue.set(true)
      }

      bool("daggerFactories") {
        description.set("Test Anvil's Dagger factory generation")
        defaultValue.set(true)
      }
    }

    group("versions") {

      description.set("Dependency versions")

      group("source") {
        description.set("Versions used to compile Anvil itself")

        string("kotlin") {
          description.set("Kotlin version to test against")
          defaultValue.set(libs.versions.kotlin)
        }
      }

      group("target") {
        description.set("Versions used by a test project that applies the Anvil plugin")

        string("kotlin") {
          description.set("Kotlin version to test against")
          defaultValue.set(libs.versions.kotlin)
        }
      }
    }
  }
}
