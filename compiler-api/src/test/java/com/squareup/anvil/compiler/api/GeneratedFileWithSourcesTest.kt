package com.squareup.anvil.compiler.api

import com.rickbusarow.kase.DefaultTestEnvironment
import com.rickbusarow.kase.HasTestEnvironmentFactory
import com.rickbusarow.kase.NoParamTestEnvironmentFactory
import com.rickbusarow.kase.files.TestLocation
import com.rickbusarow.kase.stdlib.createSafely
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.string.shouldContain
import org.junit.Test
import java.io.File
import kotlin.LazyThreadSafetyMode.NONE

internal class GeneratedFileWithSourcesTest :
  HasTestEnvironmentFactory<GeneratedFileTestEnvironment.Companion> {

  override val testEnvironmentFactory = GeneratedFileTestEnvironment

  @Test
  fun `a file declaring itself as a source file throws an exception`() = test {

    val exception = shouldThrow<AnvilCompilationException> {
      GeneratedFileWithSources(
        file = generated1,
        content = generated1.readText(),
        sourceFiles = setOf(source1, generated1),
      )
    }

    exception.message shouldContain
      """
      GeneratedFileWithSources must not contain the generated file as a source file.

      source files:
      $generated1
      $source1

      generated file:
      $generated1
      """.trimIndent()
  }

  @Test
  fun `a file with a relative file as a source throws an exception`() = test {

    val exception = shouldThrow<AnvilCompilationException> {
      GeneratedFileWithSources(
        file = generated1,
        content = generated1.readText(),
        sourceFiles = setOf(
          // make the source relative to the System user directory so that it will still resolve
          source1.relativeTo(userDir),
        ),
      )
    }

    val workingDirRelative = workingDir.relativeTo(userDir)

    exception.message shouldContain
      """
      All source files must be:
        - absolute paths
        - actual files (not directories)
        - existent in the file system
      
      not absolute:
      ${workingDirRelative.resolve("src/source1.kt")}
      
      generated file:
      $generated1
      """.trimIndent()
  }

  @Test
  fun `a file with a directory as a source throws an exception`() = test {

    val exception = shouldThrow<AnvilCompilationException> {
      GeneratedFileWithSources(
        file = generated1,
        content = generated1.readText(),
        sourceFiles = setOf(source1.parentFile),
      )
    }

    exception.message shouldContain
      """
      All source files must be:
        - absolute paths
        - actual files (not directories)
        - existent in the file system
      
      not files:
      ${source1.parentFile}
      
      generated file:
      $generated1
      """.trimIndent()
  }

  @Test
  fun `a file with a source file that doesn't exist throws an exception`() = test {

    val noFile = workingDir.resolve("noFile.kt")

    val exception = shouldThrow<AnvilCompilationException> {
      GeneratedFileWithSources(
        file = generated1,
        content = generated1.readText(),
        sourceFiles = setOf(noFile),
      )
    }

    exception.message shouldContain
      """
      All source files must be:
        - absolute paths
        - actual files (not directories)
        - existent in the file system
      
      not files:
      $noFile
      
      generated file:
      $generated1
      """.trimIndent()
  }

  @Test
  fun `a file with a no source files is allowed`() = test {

    shouldNotThrowAny {
      GeneratedFileWithSources(
        file = generated1,
        content = generated1.readText(),
        sourceFiles = setOf(),
      )
    }
  }
}

internal class GeneratedFileTestEnvironment(
  names: List<String>,
  testLocation: TestLocation,
) : DefaultTestEnvironment(names = names, testLocation = testLocation) {

  val userDir by lazy(NONE) { File(System.getProperty("user.dir")) }

  val source1 by lazy(NONE) {
    workingDir.resolve("src/${"source1.kt"}")
      .also { it.createSafely("content for ${"source1.kt"}") }
  }

  val generated1 by lazy(NONE) {
    workingDir.resolve("generated/${"generated1.kt"}")
      .also { it.createSafely("content for ${"generated1.kt"}") }
  }

  companion object : NoParamTestEnvironmentFactory<GeneratedFileTestEnvironment> {
    override fun create(
      names: List<String>,
      location: TestLocation,
    ): GeneratedFileTestEnvironment = GeneratedFileTestEnvironment(
      names = names,
      testLocation = location,
    )
  }
}
