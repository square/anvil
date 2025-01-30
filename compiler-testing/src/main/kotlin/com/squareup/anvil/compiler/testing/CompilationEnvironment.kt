package com.squareup.anvil.compiler.testing

import com.rickbusarow.kase.DefaultTestEnvironment
import com.rickbusarow.kase.HasTestEnvironmentFactory
import com.rickbusarow.kase.KaseTestFactory
import com.rickbusarow.kase.ParamTestEnvironmentFactory
import com.rickbusarow.kase.TestEnvironment
import com.rickbusarow.kase.files.HasWorkingDir

public interface K2CodeGenerator

public interface CompilationTest<PARAM, ENV : TestEnvironment> : KaseTestFactory<PARAM, ENV, ParamTestEnvironmentFactory<PARAM, ENV>>

public interface DefaultTestEnvironmentTest : HasTestEnvironmentFactory<DefaultTestEnvironment.Factory> {
  override val testEnvironmentFactory: DefaultTestEnvironment.Factory
    get() = DefaultTestEnvironment.Factory()
}

public interface CompilationEnvironment : HasWorkingDir {
  public val mode: CompilationMode
    get() = CompilationMode.K2(useKapt = false)
}
