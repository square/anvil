package com.squareup.anvil.compiler.testing

import com.rickbusarow.kase.DefaultTestEnvironment
import com.rickbusarow.kase.HasTestEnvironmentFactory
import com.rickbusarow.kase.KaseTestFactory
import com.rickbusarow.kase.ParamTestEnvironmentFactory
import com.rickbusarow.kase.TestEnvironment
import com.rickbusarow.kase.files.HasWorkingDir
import com.squareup.anvil.compiler.api.CodeGenerator
import com.squareup.anvil.compiler.internal.testing.AnvilCompilationMode
import com.squareup.anvil.compiler.internal.testing.ComponentProcessingMode
import com.squareup.anvil.compiler.internal.testing.compileAnvil
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import org.intellij.lang.annotations.Language
import java.io.File

interface CompilationTest<PARAM, ENV : TestEnvironment> : KaseTestFactory<PARAM, ENV, ParamTestEnvironmentFactory<PARAM, ENV>>

interface DefaultTestEnvironmentTest : HasTestEnvironmentFactory<DefaultTestEnvironment.Factory> {
  override val testEnvironmentFactory: DefaultTestEnvironment.Factory
    get() = DefaultTestEnvironment.Factory()
}

interface CompilationEnvironment : HasWorkingDir {
  val mode: AnvilCompilationMode
    get() = AnvilCompilationMode.Embedded(emptyList())

  fun compile(
    @Language("kotlin") vararg sources: String,
    previousCompilationResult: JvmCompilationResult? = null,
    componentProcessingMode: ComponentProcessingMode = ComponentProcessingMode.NONE,
    trackSourceFiles: Boolean = true,
    generateDaggerFactories: Boolean = false,
    disableComponentMerging: Boolean = false,
    codeGenerators: List<CodeGenerator> = emptyList(),
    allWarningsAsErrors: Boolean = true,
    mode: AnvilCompilationMode = modeDefault(codeGenerators),
    workingDir: File? = this@CompilationEnvironment.workingDir,
    expectExitCode: KotlinCompilation.ExitCode = KotlinCompilation.ExitCode.OK,
    block: JvmCompilationResult.() -> Unit = { },
  ): JvmCompilationResult = compileAnvil(
    sources = sources,
    allWarningsAsErrors = allWarningsAsErrors,
    previousCompilationResult = previousCompilationResult,
    componentProcessingMode = componentProcessingMode,
    generateDaggerFactories = generateDaggerFactories,
    disableComponentMerging = disableComponentMerging,
    trackSourceFiles = trackSourceFiles,
    mode = mode,
    workingDir = workingDir,
    expectExitCode = expectExitCode,
    block = block,
  )
}

private fun CompilationEnvironment.modeDefault(
  codeGenerators: List<CodeGenerator>,
): AnvilCompilationMode = (mode as? AnvilCompilationMode.Embedded)
  ?.copy(codeGenerators = codeGenerators)
  ?: mode
