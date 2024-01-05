package com.squareup.anvil.plugin

import com.rickbusarow.kase.KaseMatrix
import com.rickbusarow.kase.gradle.GradleKotlinTestVersions
import com.rickbusarow.kase.gradle.GradleTestEnvironment
import com.rickbusarow.kase.gradle.KaseGradleTest
import com.rickbusarow.kase.gradle.dsl.BuildFileSpec
import com.rickbusarow.kase.gradle.versions
import com.rickbusarow.kase.stdlib.letIf
import com.squareup.anvil.plugin.buildProperties.anvilVersion
import com.squareup.anvil.plugin.buildProperties.fullTestRun
import com.squareup.anvil.plugin.buildProperties.localBuildM2Dir
import java.io.File

abstract class BaseGradleTest(
  override val kaseMatrix: KaseMatrix = AnvilVersionMatrix(),
) : KaseGradleTest<GradleKotlinTestVersions>,
  FileStubs,
  MoreAsserts {

  override val localM2Path: File
    get() = localBuildM2Dir

  override val kases: List<GradleKotlinTestVersions>
    get() = kaseMatrix.versions(GradleKotlinTestVersions)
      .letIf(!fullTestRun) { it.takeLast(1) }

  /** Brings the magic of LibrariesForLibs into the test environment. */
  // TODO (rbusarow)
  //  Maybe auto-generate this from the version catalog and scope it to the environment?
  val libs = Libs()

  /** Forced overload so that tests don't reference the generated BuildProperties file directly */
  @Suppress("UnusedReceiverParameter")
  val GradleTestEnvironment.anvilVersion: String
    get() = com.squareup.anvil.plugin.buildProperties.anvilVersion

  override fun buildFileDefault(versions: GradleKotlinTestVersions): BuildFileSpec = BuildFileSpec {
    plugins {
      kotlin("jvm", versions.kotlinVersion)
      id("com.squareup.anvil", version = anvilVersion)
    }

    anvil {
      generateDaggerFactories.set(true)
    }

    dependencies {
      compileOnly(libs.dagger2.annotations)
    }
  }
}
