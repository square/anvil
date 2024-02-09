package com.squareup.anvil.plugin

import com.rickbusarow.kase.KaseMatrix
import com.rickbusarow.kase.files.HasWorkingDir
import com.rickbusarow.kase.files.JavaFileFileInjection
import com.rickbusarow.kase.files.LanguageInjection
import com.rickbusarow.kase.files.TestLocation
import com.rickbusarow.kase.gradle.DefaultGradleTestEnvironment
import com.rickbusarow.kase.gradle.DslLanguage
import com.rickbusarow.kase.gradle.DslLanguage.KotlinDsl
import com.rickbusarow.kase.gradle.GradleDependencyVersion
import com.rickbusarow.kase.gradle.GradleKotlinTestVersions
import com.rickbusarow.kase.gradle.GradleProjectBuilder
import com.rickbusarow.kase.gradle.GradleRootProjectBuilder
import com.rickbusarow.kase.gradle.GradleTestEnvironment
import com.rickbusarow.kase.gradle.GradleTestEnvironmentFactory
import com.rickbusarow.kase.gradle.KaseGradleTest
import com.rickbusarow.kase.gradle.dsl.BuildFileSpec
import com.rickbusarow.kase.gradle.rootProject
import com.rickbusarow.kase.gradle.versions
import com.rickbusarow.kase.stdlib.letIf
import com.squareup.anvil.plugin.buildProperties.anvilVersion
import com.squareup.anvil.plugin.buildProperties.fullTestRun
import com.squareup.anvil.plugin.buildProperties.localBuildM2Dir
import java.io.File

abstract class BaseGradleTest(
  override val kaseMatrix: KaseMatrix = AnvilVersionMatrix(),
) : KaseGradleTest<GradleKotlinTestVersions, AnvilGradleTestEnvironment, AnvilGradleTestEnvironment.Factory>,
  FileStubs,
  MoreAsserts {

  override val testEnvironmentFactory = AnvilGradleTestEnvironment.Factory()

  override val params: List<GradleKotlinTestVersions>
    get() = kaseMatrix.versions(GradleKotlinTestVersions)
      .letIf(!fullTestRun) { it.takeLast(1) }

  /** Forced overload so that tests don't reference the generated BuildProperties file directly */
  @Suppress("UnusedReceiverParameter")
  val GradleTestEnvironment.anvilVersion: String
    get() = com.squareup.anvil.plugin.buildProperties.anvilVersion
}

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

  /** `workingDir.resolve("build/anvil/main/generated")` */
  val GradleTestEnvironment.rootAnvilMainGenerated: File
    get() = workingDir.anvilMainGenerated

  val GradleProjectBuilder.buildFileAsFile: File
    get() = path.resolve(dslLanguage.buildFileName)
  val GradleProjectBuilder.settingsFileAsFile: File
    get() = path.resolve(dslLanguage.settingsFileName)

  class Factory : GradleTestEnvironmentFactory<GradleKotlinTestVersions, AnvilGradleTestEnvironment> {

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

    override fun create(
      params: GradleKotlinTestVersions,
      names: List<String>,
      location: TestLocation,
    ): AnvilGradleTestEnvironment {
      val hasWorkingDir = HasWorkingDir(names, location)
      return AnvilGradleTestEnvironment(
        gradleVersion = params.gradle,
        dslLanguage = KotlinDsl(useInfix = true, useLabels = false),
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
