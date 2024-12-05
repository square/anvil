package com.squareup.anvil.plugin.testing

import com.rickbusarow.kase.files.HasWorkingDir
import com.rickbusarow.kase.files.JavaFileFileInjection
import com.rickbusarow.kase.files.LanguageInjection
import com.rickbusarow.kase.files.TestLocation
import com.rickbusarow.kase.gradle.DefaultGradleTestEnvironment
import com.rickbusarow.kase.gradle.DslLanguage
import com.rickbusarow.kase.gradle.GradleDependencyVersion
import com.rickbusarow.kase.gradle.GradleKotlinTestVersions
import com.rickbusarow.kase.gradle.GradleProjectBuilder
import com.rickbusarow.kase.gradle.GradleRootProjectBuilder
import com.rickbusarow.kase.gradle.GradleTestEnvironment
import com.rickbusarow.kase.gradle.GradleTestEnvironmentFactory
import com.rickbusarow.kase.gradle.HasAgpDependencyVersion
import com.rickbusarow.kase.gradle.dsl.BuildFileSpec
import com.rickbusarow.kase.gradle.dsl.SettingsFileSpec
import com.rickbusarow.kase.gradle.rootProject
import com.squareup.anvil.plugin.buildProperties.anvilVersion
import com.squareup.anvil.plugin.buildProperties.localBuildM2Dir
import java.io.File

class AnvilGradleTestEnvironment(
  gradleVersion: GradleDependencyVersion,
  override val dslLanguage: DslLanguage,
  hasWorkingDir: HasWorkingDir,
  override val rootProject: GradleRootProjectBuilder,
  val libs: Libs,
) : DefaultGradleTestEnvironment(
  gradleVersion = gradleVersion,
  dslLanguage = dslLanguage,
  hasWorkingDir = hasWorkingDir,
  rootProject = rootProject,
),
  LanguageInjection<File> by LanguageInjection(JavaFileFileInjection()),
  AnvilFilePathExtensions {

  /** `rootProject.path.resolve("build/anvil/main/generated")` */
  val GradleTestEnvironment.rootAnvilMainGenerated: File
    get() = rootProject.generatedDir()

  val GradleProjectBuilder.buildFileAsFile: File
    get() = path.resolve(dslLanguage.buildFileName)
  val GradleProjectBuilder.settingsFileAsFile: File
    get() = path.resolve(dslLanguage.settingsFileName)

  /** Resolves the main sourceset generated directory for Anvil. */
  fun GradleProjectBuilder.generatedDir(): File = path.buildAnvilMainGenerated

  /**
   * ```
   * rootAnvilMainGenerated.listRelativeFilePaths() shouldBe listOf(
   *     "com/squareup/test/InjectClass_Factory.kt",
   *     "com/squareup/test/OtherClass_Factory.kt",
   *   )
   * ```
   */
  fun File.listRelativeFilePaths(parentDir: File = this): List<String> = walkBottomUp()
    .filter { it.isFile }
    .map { it.toRelativeString(parentDir) }
    .sorted()
    .toList()

  override fun toString(): String = ""

  class Factory :
    GradleTestEnvironmentFactory<GradleKotlinTestVersions, AnvilGradleTestEnvironment> {

    override val localM2Path: File
      get() = localBuildM2Dir

    override fun buildFileDefault(versions: GradleKotlinTestVersions): BuildFileSpec =
      BuildFileSpec {
        plugins {
          kotlin("jvm", versions.kotlinVersion)
          id("com.squareup.anvil", version = anvilVersion)
        }

        anvil {
          generateDaggerFactories.set(true)
        }
        val libs = Libs()
        dependencies {
          compileOnly(libs.dagger2.annotations)
        }
      }

    override fun settingsFileDefault(versions: GradleKotlinTestVersions): SettingsFileSpec {
      return SettingsFileSpec {
        rootProjectName.setEquals("root")

        pluginManagement {
          repositories {
            maven(localM2Path)
            gradlePluginPortal()
            mavenCentral()
            google()
          }
          plugins {
            kotlin("android", version = versions.kotlinVersion, apply = false)
            kotlin("jvm", version = versions.kotlinVersion, apply = false)
            kotlin("kapt", version = versions.kotlinVersion, apply = false)
            id("com.squareup.anvil", version = anvilVersion, apply = false)
            if (versions is HasAgpDependencyVersion) {
              id("com.android.application", versions.agpVersion, apply = false)
              id("com.android.library", versions.agpVersion, apply = false)
            }
          }
        }
        dependencyResolutionManagement {
          repositories {
            maven(localM2Path)
            gradlePluginPortal()
            mavenCentral()
            google()
          }
        }
      }
    }

    override fun create(
      params: GradleKotlinTestVersions,
      names: List<String>,
      location: TestLocation,
    ): AnvilGradleTestEnvironment {
      val hasWorkingDir = HasWorkingDir(names, location)
      return AnvilGradleTestEnvironment(
        gradleVersion = params.gradle,
        dslLanguage = DslLanguage.KotlinDsl(useInfix = true, useLabels = false),
        hasWorkingDir = hasWorkingDir,
        rootProject = rootProject(
          path = hasWorkingDir.workingDir,
          dslLanguage = dslLanguage,
        ) {
          buildFile(buildFileDefault(params))
          settingsFile(settingsFileDefault(params))
        },

        // TODO (rbusarow)
        //  Maybe auto-generate this from the version catalog and scope it to the environment?
        libs = Libs(),
      )
    }
  }
}
