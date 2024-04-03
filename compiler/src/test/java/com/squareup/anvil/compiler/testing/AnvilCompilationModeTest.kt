package com.squareup.anvil.compiler.testing

import com.rickbusarow.kase.DefaultTestEnvironment
import com.rickbusarow.kase.NoParamTestEnvironmentFactory
import com.rickbusarow.kase.files.HasWorkingDir
import com.rickbusarow.kase.files.TestLocation
import com.squareup.anvil.compiler.internal.testing.AnvilCompilationMode

class AnvilEmbeddedCompilationTestEnvironment(
  hasWorkingDir: HasWorkingDir,
) : DefaultTestEnvironment(hasWorkingDir),
  CompilationEnvironment {

  override val mode: AnvilCompilationMode = AnvilCompilationMode.Embedded()

  companion object Factory : NoParamTestEnvironmentFactory<AnvilEmbeddedCompilationTestEnvironment> {
    override fun create(
      names: List<String>,
      location: TestLocation,
    ): AnvilEmbeddedCompilationTestEnvironment = AnvilEmbeddedCompilationTestEnvironment(
      hasWorkingDir = HasWorkingDir(names, location),
    )
  }
}
