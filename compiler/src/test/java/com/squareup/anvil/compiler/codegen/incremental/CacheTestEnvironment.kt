package com.squareup.anvil.compiler.codegen.incremental

import com.rickbusarow.kase.DefaultTestEnvironment
import com.rickbusarow.kase.EnvironmentTests
import com.rickbusarow.kase.HasTestEnvironmentFactory
import com.rickbusarow.kase.NoParamTestEnvironmentFactory
import com.rickbusarow.kase.files.TestLocation
import com.rickbusarow.kase.stdlib.createSafely
import com.rickbusarow.kase.stdlib.div
import com.squareup.anvil.compiler.api.FileWithContent
import com.squareup.anvil.compiler.api.GeneratedFileWithSources
import com.squareup.anvil.compiler.mapToSet
import io.kotest.matchers.file.shouldExist
import java.io.File
import kotlin.properties.ReadOnlyProperty
import io.kotest.matchers.shouldBe as kotestShouldBe

internal interface CacheTests :
  EnvironmentTests<Any, CacheTestEnvironment, CacheTestEnvironment.Factory>,
  HasTestEnvironmentFactory<CacheTestEnvironment.Factory> {

  override val testEnvironmentFactory: CacheTestEnvironment.Factory
    get() = CacheTestEnvironment.Factory()
}

internal class CacheTestEnvironment(
  names: List<String>,
  testLocation: TestLocation,
) : DefaultTestEnvironment(names = names, testLocation = testLocation) {

  val projectDir = BaseDir.ProjectDir(workingDir)
  val buildDir = BaseDir.BuildDir(workingDir / "build")

  val generatedDir = buildDir.file / "anvil" / "generated"
  val binaryFile = buildDir.file / "anvil" / "cache" / "cache.bin"

  val cache = GeneratedFileCache.fromFile(
    binaryFile = binaryFile,
    projectDir = projectDir,
    buildDir = buildDir,
  )
  val fileOperations = FileCacheOperations(cache = cache)

  val source1 by projectDir.sourceFile()
  val source2 by projectDir.sourceFile()
  val source3 by projectDir.sourceFile()
  val source4 by projectDir.sourceFile()
  val source5 by projectDir.sourceFile()

  val gen1 by generatedDir.generatedFile()
  val gen2 by generatedDir.generatedFile()
  val gen3 by generatedDir.generatedFile()
  val gen4 by generatedDir.generatedFile()
  val gen5 by generatedDir.generatedFile()
  val gen6 by generatedDir.generatedFile()
  val gen7 by generatedDir.generatedFile()
  val gen8 by generatedDir.generatedFile()
  val gen9 by generatedDir.generatedFile()

  val GeneratedFileWithSources.absolute: AbsoluteFile
    get() = AbsoluteFile(file)

  internal operator fun BaseDir.div(relativePath: String): AbsoluteFile = resolve(relativePath)
  internal fun BaseDir.resolve(relativePath: String): AbsoluteFile {
    return AbsoluteFile(file / relativePath)
  }

  fun currentFiles(): List<AbsoluteFile> = workingDir.currentFiles()

  fun BaseDir.currentFiles(): List<AbsoluteFile> = file.currentFiles()
  fun File.currentFiles(): List<AbsoluteFile> = walkTopDown()
    .filter { it.isFile && it != binaryFile }
    .mapTo(mutableListOf(), ::AbsoluteFile)
    .sorted()

  internal fun BaseDir.sourceFile(name: String? = null): ReadOnlyProperty<Any?, AbsoluteFile> {
    var file: AbsoluteFile? = null
    return ReadOnlyProperty { _, property ->
      file ?: sourceFile(name = name?.let { "$it.kt" } ?: "${property.name}.kt")
        .also { file = it }
    }
  }

  private fun BaseDir.sourceFile(name: String): AbsoluteFile =
    AbsoluteFile(file / "src" / name)
      .also { it.file.createSafely("content for $name") }

  internal fun File.generatedFile(
    vararg sources: Comparable<AbsoluteFile>,
    name: String? = null,
  ): ReadOnlyProperty<Any?, GeneratedFileWithSources> {
    var file: GeneratedFileWithSources? = null
    return ReadOnlyProperty { _, property ->
      file ?: run {
        val fileName = name?.let { "$it.kt" } ?: "${property.name}.kt"
        val content = "content for $name"
        GeneratedFileWithSources(
          file = resolve(relative = fileName).createSafely(content = content),
          content = content,
          sourceFiles = sources.mapToSet { it.absoluteFile },
        )
          .also { file = it }
      }
    }
  }

  fun AbsoluteFile.writeText(content: String) {
    file.createSafely(content = content)
  }

  fun GeneratedFileCache.addAll(vararg generated: GeneratedFileWithSources) {
    for (gen in generated) {
      addGeneratedFile(gen)
    }
  }

  fun GeneratedFileWithSources.withSources(
    vararg sources: Comparable<AbsoluteFile>,
  ): GeneratedFileWithSources {
    return GeneratedFileWithSources(
      file = file,
      content = content,
      sourceFiles = sourceFiles + sources.map { it.absoluteFile },
    )
  }

  // NB: This `Comparable<AbsoluteFile>` can only be an `AbsoluteFile`,
  // but the compiler doesn't support vararg with an explicit JvmInline value class,
  // due to the inline mangling. So we use this instead.
  val Comparable<AbsoluteFile>.absoluteFile: File
    get() = (this as AbsoluteFile).file

  fun FileCacheOperations.addToCache(vararg gens: Any) {
    check(gens.all { it is AbsoluteFile || it is GeneratedFileWithSources }) {
      "Only AbsoluteFile and GeneratedFileWithSources are supported."
    }
    addToCache(
      sourceFiles = gens.filterIsInstance<AbsoluteFile>(),
      filesWithSources = gens.filterIsInstance<GeneratedFileWithSources>(),
    )
  }

  fun FileCacheOperations.restoreFromCache(vararg sources: Comparable<AbsoluteFile>) {
    @Suppress("UNCHECKED_CAST")
    restoreFromCache(
      generatedDir = generatedDir,
      inputKtFiles = sources.toSet() as Set<AbsoluteFile>,
    )
  }

  fun newCache(binaryFile: File = this.binaryFile): GeneratedFileCache {
    return GeneratedFileCache.fromFile(
      binaryFile = binaryFile,
      projectDir = projectDir,
      buildDir = buildDir,
    )
  }

  infix fun File.shouldExistWithText(expectedText: String) {
    shouldExist()
    readText() kotestShouldBe expectedText
  }

  infix fun Collection<AbsoluteFile>.shouldBe(expected: Iterable<Any>) {
    val expectedAbsolute = expected.map {
      when (it) {
        is AbsoluteFile -> it
        is FileWithContent -> AbsoluteFile(it.file)
        else -> error("Unsupported type: $it")
      }
    }
    sorted() kotestShouldBe expectedAbsolute.sorted()
  }

  class Factory : NoParamTestEnvironmentFactory<CacheTestEnvironment> {
    override fun create(
      names: List<String>,
      location: TestLocation,
    ): CacheTestEnvironment = CacheTestEnvironment(names = names, testLocation = location)
  }
}
