package com.squareup.anvil.plugin.testing

import com.rickbusarow.kase.KaseMatrix
import com.rickbusarow.kase.gradle.GradleKotlinTestVersions
import com.rickbusarow.kase.gradle.GradleTestEnvironment
import com.rickbusarow.kase.gradle.KaseGradleTest
import com.rickbusarow.kase.gradle.versions
import com.rickbusarow.kase.stdlib.letIf
import com.squareup.anvil.compiler.testing.MoreAsserts
import com.squareup.anvil.plugin.buildProperties.fullTestRun
import com.squareup.anvil.plugin.testing.AnvilGradleTestEnvironment.Factory

abstract class BaseGradleTest(
  override val kaseMatrix: KaseMatrix = AnvilVersionMatrix(),
) : KaseGradleTest<GradleKotlinTestVersions, AnvilGradleTestEnvironment, Factory>,
  FileStubs,
  MoreAsserts,
  ClassGraphAsserts {

  override val testEnvironmentFactory = Factory()

  override val params: List<GradleKotlinTestVersions>
    get() = kaseMatrix.versions(GradleKotlinTestVersions)
      .letIf(!fullTestRun) { it.takeLast(1) }

  /** Forced overload so that tests don't reference the generated BuildProperties file directly */
  @Suppress("UnusedReceiverParameter")
  val GradleTestEnvironment.anvilVersion: String
    get() = com.squareup.anvil.plugin.buildProperties.anvilVersion
}
