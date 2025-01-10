package com.squareup.anvil.compiler.testing

import com.rickbusarow.kase.DefaultTestEnvironment
import com.rickbusarow.kase.HasTestEnvironmentFactory
import com.rickbusarow.kase.KaseTestFactory
import com.rickbusarow.kase.ParamTestEnvironmentFactory
import com.rickbusarow.kase.TestEnvironment
import com.rickbusarow.kase.files.HasWorkingDir
import com.tschuchort.compiletesting.JvmCompilationResult
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.cli.common.ExitCode
import java.io.File

public interface K2CodeGenerator

public interface CompilationTest<PARAM, ENV : TestEnvironment> : KaseTestFactory<PARAM, ENV, ParamTestEnvironmentFactory<PARAM, ENV>>

public interface DefaultTestEnvironmentTest : HasTestEnvironmentFactory<DefaultTestEnvironment.Factory> {
  override val testEnvironmentFactory: DefaultTestEnvironment.Factory
    get() = DefaultTestEnvironment.Factory()
}

public interface CompilationEnvironment : HasWorkingDir {
  public val mode: CompilationMode
    get() = CompilationMode.K2(useKapt = false)

  public fun compile(
    @Language("kotlin") vararg sources: String,
    previousCompilationResult: JvmCompilationResult? = null,
    enableDaggerAnnotationProcessor: Boolean = false,
    trackSourceFiles: Boolean = true,
    generateDaggerFactories: Boolean = false,
    disableComponentMerging: Boolean = false,
    codeGenerators: List<K2CodeGenerator> = emptyList(),
    allWarningsAsErrors: Boolean = true,
    mode: CompilationMode = CompilationMode.K2(useKapt = true),
    workingDir: File? = this@CompilationEnvironment.workingDir,
    expectExitCode: ExitCode = ExitCode.OK,
    block: JvmCompilationResult.() -> Unit = { },
  ): JvmCompilationResult = TODO()
}
