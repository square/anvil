package com.squareup.anvil.conventions.versions

import com.github.gmazzo.buildconfig.BuildConfigExtension
import com.rickbusarow.kgx.libsCatalog
import com.rickbusarow.kgx.listProperty
import com.rickbusarow.kgx.pluginId
import com.rickbusarow.kgx.propertyOrNull
import com.rickbusarow.kgx.version
import com.squareup.anvil.conventions.versions.Versions.Companion.agpListDefault
import com.squareup.anvil.conventions.versions.Versions.Companion.anvilListDefault
import com.squareup.anvil.conventions.versions.Versions.Companion.gradleListDefault
import com.squareup.anvil.conventions.versions.Versions.Companion.kotlinListDefault
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.kotlin.dsl.buildConfigField
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import java.io.File
import java.io.Serializable
import javax.inject.Inject

open class Versions @Inject constructor(
  private val target: Project,
) : Serializable {

  val gradleList: ListProperty<String> = target.objects
    .listProperty<String>()
    .convention(propertyName = "modulecheck.gradleVersion", gradleListDefault)

  val agpList: ListProperty<String> = target.objects
    .listProperty<String>()
    .convention("modulecheck.agpVersion", agpListDefault)

  val anvilList: ListProperty<String> = target.objects
    .listProperty<String>()
    .convention("modulecheck.anvilVersion", anvilListDefault)

  val kotlinList: ListProperty<String> = target.objects
    .listProperty<String>()
    .convention("modulecheck.kotlinVersion", kotlinListDefault)

  private fun ListProperty<String>.convention(
    propertyName: String,
    default: List<String>,
  ): ListProperty<String> = convention(
    target.providers.gradleProperty(propertyName)
      .map { it.split("""\s*,\s*""".toRegex()) }
      .orElse(default),
  )

  companion object {
    internal val gradleListDefault = listOf("8.5")
    internal val agpListDefault = listOf("8.0.2", "8.1.0")
    internal val anvilListDefault = listOf("2.4.9")
    internal val kotlinListDefault = listOf("1.9.22")
  }
}

open class VersionsMatrixExtension @Inject constructor(target: Project) : Serializable {

  val versions = Versions(target)

  fun Project.versionsMatrix(sourceSetName: String, packageName: String) {
    setUpGeneration(
      sourceSetName = sourceSetName,
      packageName = packageName,
      versions = versions,
    )
  }
}

private fun Project.setUpGeneration(
  sourceSetName: String,
  packageName: String,
  versions: Versions,
) {

  val generatedDirPath = layout.buildDirectory.dir(
    "generated/sources/versionsMatrix/kotlin/main",
  )

  requireInSyncWithToml()

  plugins.apply(libsCatalog.pluginId("buildconfig"))

  extensions.configure(BuildConfigExtension::class.java) { extension ->
    extension.sourceSets.named(sourceSetName) { sourceSet ->
      sourceSet.forClass(packageName, "Versions") { clazz ->
        clazz.buildConfigField("gradleList", versions.gradleList.get())
        clazz.buildConfigField("agpList", versions.agpList.get())
        clazz.buildConfigField("anvilList", versions.anvilList.get())
        clazz.buildConfigField("kotlinList", versions.kotlinList.get())
        clazz.buildConfigField(
          "exhaustive",
          propertyOrNull<String>("modulecheck.exhaustive")?.toBoolean() ?: false,
        )
      }
    }
  }

  extensions.configure(KotlinJvmProjectExtension::class.java) { extension ->
    extension.sourceSets.named("main") { kotlinSourceSet ->
      kotlinSourceSet.kotlin.srcDir(generatedDirPath)
    }
  }
}

private fun Project.requireInSyncWithToml() {

  val simpleName = Versions::class.simpleName
  val versionsMatrixRelativePath = Versions::class.qualifiedName!!
    .replace('.', File.separatorChar)
    .let { "$it.kt" }

  val versionMatrixFile = rootDir
    .resolve("build-logic/conventions")
    .resolve("src/main/kotlin")
    .resolve(versionsMatrixRelativePath)

  require(versionMatrixFile.exists()) {
    "Could not resolve the $simpleName file: $versionMatrixFile"
  }

  VersionsMatrix(
    gradleList = gradleListDefault,
    agpList = agpListDefault,
    anvilList = anvilListDefault,
    kotlinList = kotlinListDefault,
  ).run {

    sequenceOf(
      Triple(agpList, "agpList", "androidTools"),
      Triple(anvilList, "anvilList", "square-anvil"),
      Triple(kotlinList, "kotlinList", "kotlin-core"),
    )
      .forEach { (list, listName, alias) ->
        require(list.contains(libsCatalog.version(alias))) {
          "The versions catalog version for '$alias' is ${libsCatalog.version(alias)}.  " +
            "Update the $simpleName list '$listName' to include this new version.\n" +
            "\tfile://$versionMatrixFile"
        }
      }

    require(gradleList.contains(gradle.gradleVersion)) {
      "The Gradle version is ${gradle.gradleVersion}.  " +
        "Update the $simpleName list 'gradleList' to include this new version.\n" +
        "\tfile://$versionMatrixFile"
    }
  }
}

data class TestVersions(
  val gradle: String,
  val agp: String,
  val anvil: String,
  val kotlin: String,
) {
  override fun toString(): String {
    return "[gradle $gradle, agp $agp, anvil $anvil, kotlin $kotlin]"
  }
}
