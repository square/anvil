package com.squareup.anvil.builds.settings

import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.api.initialization.dsl.VersionCatalogBuilder
import org.gradle.api.internal.file.FileOperations
import java.io.File
import java.net.URI
import javax.inject.Inject

abstract class SettingsPlugin @Inject constructor(
  private val fileOperations: FileOperations,
) : Plugin<Settings> {

  override fun apply(target: Settings) {
    target.dependencyResolutionManagement.versionCatalogs { container ->

      val catalogBuilder = container.maybeCreate("libs")

      val maybeFile = target.rootDir.resolveInParents("gradle/libs.versions.toml")

      if (maybeFile != target.rootDir.resolve("gradle/libs.versions.toml")) {
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
            // KCT versions after 0.3.2 are not compatible with Kotlin 1.8
            catalogBuilder.overrideVersion(alias = "kct", versionString = "0.3.2")
          }
        }

      catalogBuilder.version("config-warningsAsErrors", System.getenv("CI") ?: "false")
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

  private fun File.resolveInParents(relativePath: String): File {
    return resolve(relativePath).takeIf { it.exists() }
      ?: parentFile?.resolveInParents(relativePath)
      ?: error("File $relativePath not found")
  }
}
