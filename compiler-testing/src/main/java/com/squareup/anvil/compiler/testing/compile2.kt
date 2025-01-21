package com.squareup.anvil.compiler.testing

import com.rickbusarow.kase.stdlib.createSafely
import com.rickbusarow.kase.stdlib.div
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.config.LanguageVersion
import java.io.File

public fun CompilationEnvironment.compile2(
  @Language("kotlin") vararg kotlinSources: String,
  javaSources: List<String> = emptyList(),
  expectedExitCode: ExitCode = ExitCode.OK,
  exec: Compile2Result.() -> Unit = {},
): Boolean {
  val sourceFiles = kotlinSources.mapIndexed { i, kotlinNotTrimmed ->
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
    sourceFiles = sourceFiles,
    javaFiles = javaFiles,
    expectedExitCode = expectedExitCode,
    exec = exec,
  )
}

public fun CompilationEnvironment.compile2(
  sourceFiles: List<File>,
  javaFiles: List<File>,
  languageVersion: LanguageVersion = LanguageVersion.KOTLIN_2_0,
  expectedExitCode: ExitCode = ExitCode.OK,
  exec: Compile2Result.() -> Unit = {},
): Boolean {

  val kaptDir = workingDir / "kapt"

  val config = Compile2CompilationConfiguration(
    sourceFiles = sourceFiles + javaFiles,
    useKapt = mode.useKapt,
    verbose = false,
    allWarningsAsErrors = true,
    jarOutputDir = workingDir / "libs",
    classFilesDir = workingDir / "kotlin/classes",
    kaptStubsDir = kaptDir / "stubs",
    kaptGeneratedSourcesDir = kaptDir / "generated",
    kaptIncrementalDir = kaptDir / "incremental",
    kaptClassesDir = kaptDir / "classes",
    languageVersion = languageVersion,
  )

  val compilation = Compile2Compilation(config, expectedExitCode)

  val result = compilation.execute()

  result.exec()

  return true
}

internal fun javaHome(): File = javaHomeOrNull() ?: error("JAVA_HOME and 'java.home' not set")

internal fun javaHomeOrNull(): File? {
  val path = System.getProperty("java.home")
    ?: System.getenv("JAVA_HOME")
    ?: return null

  return File(path).also { check(it.isDirectory) }
}

internal fun Iterable<File>.pathStrings(): List<String> = map { it.absolutePath }.distinct()
internal fun Iterable<File>.classpathString(): String =
  pathStrings().joinToString(File.pathSeparator)
