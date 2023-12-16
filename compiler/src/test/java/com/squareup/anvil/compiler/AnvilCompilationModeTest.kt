package com.squareup.anvil.compiler

import com.rickbusarow.kase.DefaultTestEnvironment
import com.rickbusarow.kase.Kase1
import com.rickbusarow.kase.KaseTestFactory
import com.rickbusarow.kase.ParamTestEnvironmentFactory
import com.rickbusarow.kase.files.HasWorkingDir
import com.rickbusarow.kase.files.TestLocation
import com.rickbusarow.kase.kases
import com.rickbusarow.kase.stdlib.letIf
import com.squareup.anvil.compiler.internal.testing.AnvilCompilationMode

class AnvilCompilationModeTestEnvironment(
  override val mode: AnvilCompilationMode,
  hasWorkingDir: HasWorkingDir,
) : DefaultTestEnvironment(hasWorkingDir),
  CompilationEnvironment {

  companion object : ParamTestEnvironmentFactory<
    Kase1<AnvilCompilationMode>,
    AnvilCompilationModeTestEnvironment,
    > {
    override fun create(
      params: Kase1<AnvilCompilationMode>,
      names: List<String>,
      location: TestLocation,
    ): AnvilCompilationModeTestEnvironment = AnvilCompilationModeTestEnvironment(
      mode = params.a1,
      hasWorkingDir = HasWorkingDir(names, location),
    )
  }
}

abstract class AnvilCompilationModeTest(
  modes: List<AnvilCompilationMode> = listOf(
    AnvilCompilationMode.Embedded(),
    AnvilCompilationMode.Ksp(),
  ),
) : KaseTestFactory<
  Kase1<AnvilCompilationMode>,
  AnvilCompilationModeTestEnvironment,
  ParamTestEnvironmentFactory<Kase1<AnvilCompilationMode>, AnvilCompilationModeTestEnvironment>,
  > {

  constructor(mode: AnvilCompilationMode, vararg additionalModes: AnvilCompilationMode) : this(
    listOf(mode, *additionalModes),
  )

  override val params: List<Kase1<AnvilCompilationMode>> = kases(
    modes.letIf(!isFullTestRun()) { it.take(1) },
    displayNameFactory = { "mode: ${a1::class.simpleName}" },
  )

  override val testEnvironmentFactory:
    ParamTestEnvironmentFactory<Kase1<AnvilCompilationMode>, AnvilCompilationModeTestEnvironment> =
    AnvilCompilationModeTestEnvironment.Companion
}
