package com.squareup.anvil.builds.settings

import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.api.initialization.dsl.VersionCatalogBuilder
import org.gradle.api.internal.file.FileOperations
import java.net.URI
import javax.inject.Inject

abstract class SettingsPlugin @Inject constructor(
  private val fileOperations: FileOperations,
) : Plugin<Settings> {

  override fun apply(target: Settings) {
    target.dependencyResolutionManagement.versionCatalogs { container ->

      val catalogBuilder = container.maybeCreate("libs")

      if (target.rootProject.name != "anvil") {
        val maybeFile = target.rootDir.resolve("../../gradle/libs.versions.toml")
        require(maybeFile.exists()) {
          "Expected to find libs.versions.toml at $maybeFile"
        }
        catalogBuilder.from(fileOperations.immutableFiles(maybeFile))
      }

      @Suppress("UnstableApiUsage")
      target.providers
        .systemPropertiesPrefixedBy("override_")
        .get()
        .forEach { (key, value) ->
          val alias = key.substring("override_".length)
          catalogBuilder.overrideVersion(alias = alias, versionString = value.toString())

          if (alias == "kotlin" && value.toString().startsWith("1.8")) {
            // TODO hardcoded to match what's in libs.versions.toml, but kinda ugly
            catalogBuilder.overrideVersion(alias = "ksp", versionString = "$value-1.0.11")
          }
        }
    }

    @Suppress("UnstableApiUsage")
    target.dependencyResolutionManagement.repositories { repos ->
      repos.mavenCentral()
      repos.gradlePluginPortal()
      repos.google()

      if (target.providers.gradleProperty("anvil.allowSnapshots").orNull?.toBoolean() == true) {
        repos.maven { it.url = URI("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev") }
        repos.maven { it.url = URI("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/bootstrap") }
        repos.maven { it.url = URI("https://s01.oss.sonatype.org/content/repositories/snapshots") }
        repos.maven { it.url = URI("https://oss.sonatype.org/content/repositories/snapshots") }
      }
    }
  }

  private fun VersionCatalogBuilder.overrideVersion(
    alias: String,
    versionString: String,
  ) {
    println("Overriding $alias with $versionString")
    version(alias, versionString)
  }
}
