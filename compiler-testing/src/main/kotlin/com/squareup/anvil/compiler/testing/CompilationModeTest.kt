package com.squareup.anvil.compiler.testing

import com.rickbusarow.kase.DefaultTestEnvironment
import com.rickbusarow.kase.Kase1
import com.rickbusarow.kase.KaseTestFactory
import com.rickbusarow.kase.ParamTestEnvironmentFactory
import com.rickbusarow.kase.files.HasWorkingDir
import com.rickbusarow.kase.files.TestLocation
import com.rickbusarow.kase.kase
import com.rickbusarow.kase.kases
import com.squareup.anvil.compiler.testing.classgraph.ClassGraphAsserts
import com.squareup.anvil.compiler.testing.classgraph.ClassInfoAsserts

public class CompilationModeTestEnvironment(
  override val mode: CompilationMode,
  hasWorkingDir: HasWorkingDir,
) : DefaultTestEnvironment(hasWorkingDir),
  CompilationEnvironment {

  public companion object : ParamTestEnvironmentFactory<
    Kase1<CompilationMode>,
    CompilationModeTestEnvironment,
    > {
    override fun create(
      params: Kase1<CompilationMode>,
      names: List<String>,
      location: TestLocation,
    ): CompilationModeTestEnvironment = CompilationModeTestEnvironment(
      mode = params.a1,
      hasWorkingDir = HasWorkingDir(names, location),
    )
  }
}

public sealed interface CompilationMode {
  public val useKapt: Boolean

  public data class K2(
    override val useKapt: Boolean,

  ) : CompilationMode

  @Deprecated("K1 is not implemented yet", level = DeprecationLevel.ERROR)
  public data class K1(
    override val useKapt: Boolean,

  ) : CompilationMode {
    init {
      error("K1 is not implemented yet")
    }
  }
}

public abstract class CompilationModeTest(
  modes: List<CompilationMode> = listOf(
    CompilationMode.K2(false),
  ),
) : KaseTestFactory<
  Kase1<CompilationMode>,
  CompilationModeTestEnvironment,
  ParamTestEnvironmentFactory<Kase1<CompilationMode>, CompilationModeTestEnvironment>,
  >,
  ClassGraphAsserts,
  ClassInfoAsserts,
  MoreAsserts {

  public fun test(
    mode: CompilationMode,
    names: List<String> = emptyList(),
    testLocation: TestLocation = TestLocation.get(),
    testAction: suspend CompilationModeTestEnvironment.() -> Unit,
  ) {
    test(
      param = kase(mode),
      testEnvironmentFactory = testEnvironmentFactory,
      names = names,
      testLocation = testLocation,
      testAction = testAction,
    )
  }

  public constructor(mode: CompilationMode, vararg additionalModes: CompilationMode) : this(
    listOf(mode, *additionalModes),
  )

  override val params: List<Kase1<CompilationMode>> = kases(
    modes,
    displayNameFactory = { "mode: $a1" },
  )

  override val testEnvironmentFactory:
    ParamTestEnvironmentFactory<Kase1<CompilationMode>, CompilationModeTestEnvironment> =
    CompilationModeTestEnvironment
}
