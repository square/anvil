package com.squareup.anvil.compiler.testing

import com.rickbusarow.kase.DefaultTestEnvironment
import com.rickbusarow.kase.HasTestEnvironmentFactory
import com.rickbusarow.kase.KaseTestFactory
import com.rickbusarow.kase.ParamTestEnvironmentFactory
import com.rickbusarow.kase.TestEnvironment
import com.rickbusarow.kase.files.HasWorkingDir
import com.rickbusarow.kase.stdlib.createSafely
import com.squareup.anvil.compiler.k2.fir.AnvilFirExtensionFactory
import com.squareup.anvil.compiler.testing.compilation.Compile2Compilation
import com.squareup.anvil.compiler.testing.compilation.Compile2CompilationConfiguration
import com.squareup.anvil.compiler.testing.compilation.Compile2Result
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

  /**
   * A convenience overload of [compile2] that accepts raw Kotlin and Java source code strings.
   *
   * 1. Each Kotlin source string is trimmed and written to a uniquely named `.kt` file within
   *    [workingDir]/sources.
   * 2. Each Java source string is written to a `.java` file, with filenames inferred from the
   *    `class` or `interface` declaration in the code.
   * 3. Constructs a [Compile2CompilationConfiguration] by applying any custom [configuration] block.
   * 4. Performs a compile via [Compile2Compilation] (and optional KAPT if [mode.useKapt] is `true`).
   * 5. Returns a [Compile2Result], which can be further inspected (e.g., retrieving the class loader,
   *    scanning classes via `classGraph`, or verifying code generation).
   *
   * @param kotlinSources one or more Kotlin source strings
   * @param javaSources optional Java source strings
   * @param firExtensions optional FIR extension factories for advanced compiler customization
   * @param configuration an optional config transform to modify the default [Compile2CompilationConfiguration]
   * @param expectedExitCode automatically asserted against each compilation phase's result
   * @param exec invoked with the [Compile2Result] after compilation
   * @return a [Compile2Result] referencing all compilation outputs
   * @see com.squareup.anvil.compiler.testing.CompilationEnvironment.compile2
   * @see com.squareup.anvil.compiler.testing.compilation.Compile2CompilationConfiguration
   * @see com.squareup.anvil.compiler.testing.compilation.Compile2Compilation
   * @see com.squareup.anvil.compiler.testing.compilation.Compile2Result
   * @sample com.squareup.anvil.compiler.testing.compilation.Compile2Sample.compile_source_strings
   */
  public fun compile2(
    @Language("kotlin") vararg kotlinSources: String,
    javaSources: List<String> = emptyList(),
    firExtensions: List<AnvilFirExtensionFactory<*>> = emptyList(),
    configuration: (Compile2CompilationConfiguration) -> Compile2CompilationConfiguration = { it },
    expectedExitCode: ExitCode = ExitCode.OK,
    exec: Compile2Result.() -> Unit = {},
  ): Compile2Result {
    val kotlinFiles = kotlinSources.mapIndexed { i, kotlinNotTrimmed ->
      val kotlin = kotlinNotTrimmed.trimIndent()
      val packageName = kotlin.substringAfter("package ").substringBefore("\n").trim()
      val packageDir = packageName.replace(".", "/")

      workingDir.resolve("sources/$packageDir/Source$i.kt")
        .createSafely(kotlin)
    }

    val javaFiles = javaSources.map { java ->
      val packageName = java.substringAfter("package ").substringBefore(";").trim()
      val packageDir = packageName.replace(".", "/")

      val reg = """^[^/]*(?:class|interface|enum)[\t ]+(\S+)""".toRegex()
      val className = java.lineSequence().firstNotNullOf { line ->
        reg.find(line)?.groupValues?.get(1)
      }

      workingDir.resolve("sources/$packageDir/$className.java")
        .createSafely(java)
    }

    return compile2(
      sourceFiles = kotlinFiles + javaFiles,
      firExtensions = firExtensions,
      configuration = configuration,
      expectedExitCode = expectedExitCode,
      exec = exec,
    )
  }

  /**
   * A convenience overload of [compile2] that accepts raw Kotlin and Java source code strings.
   *
   * **Steps**:
   *  1. A [Compile2CompilationConfiguration] is constructed (or passed in via [configuration]).
   *  2. A [Compile2Compilation] is instantiated to run:
   *     - KAPT (if `useKapt` is true)
   *     - Kotlin compilation
   *     - Java compilation (if any `.java` sources are present).
   *  3. Produces a [Compile2Result], which contains the compiled classes, a [ClassLoader], and
   *     utilities for scanning or packaging them (e.g., `jar` creation).
   *
   * @param sourceFiles a list of .kt or .java files to be compiled
   * @param firExtensions optional FIR extension factories for custom processing
   * @param configuration a lambda to customize the [Compile2CompilationConfiguration] before compilation
   * @param firExtensions optional FIR extension factories for advanced compiler customization
   * @param configuration an optional config transform to modify the default [Compile2CompilationConfiguration]
   * @param expectedExitCode automatically asserted against each compilation phase's result
   * @param exec invoked with the [Compile2Result] after compilation
   * @return a [Compile2Result] referencing all compilation outputs
   * @see com.squareup.anvil.compiler.testing.CompilationEnvironment.compile2
   * @see com.squareup.anvil.compiler.testing.compilation.Compile2CompilationConfiguration
   * @see com.squareup.anvil.compiler.testing.compilation.Compile2Compilation
   * @see com.squareup.anvil.compiler.testing.compilation.Compile2Result
   * @sample com.squareup.anvil.compiler.testing.compilation.Compile2Sample.compile_source_strings
   * @sample com.squareup.anvil.compiler.testing.compilation.Compile2Sample.pass_a_custom_generator
   */
  public fun compile2(
    sourceFiles: List<File>,
    firExtensions: List<AnvilFirExtensionFactory<*>> = emptyList(),
    configuration: (Compile2CompilationConfiguration) -> Compile2CompilationConfiguration = { it },
    expectedExitCode: ExitCode = ExitCode.OK,
    exec: Compile2Result.() -> Unit = {},
  ): Compile2Result {

    val config = Compile2CompilationConfiguration.default(workingDir, mode.useKapt)
      .copy(
        sourceFiles = sourceFiles,
        firExtensions = firExtensions,
      )
      .let(configuration)

    val compilation = Compile2Compilation(config, expectedExitCode)

    return compilation.execute().also { it.exec() }
  }
}
