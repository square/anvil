package com.squareup.anvil.compiler.codegen.incremental

import com.rickbusarow.kase.DefaultTestEnvironment
import com.rickbusarow.kase.EnvironmentTests
import com.rickbusarow.kase.HasTestEnvironmentFactory
import com.rickbusarow.kase.NoParamTestEnvironmentFactory
import com.rickbusarow.kase.files.TestLocation
import com.rickbusarow.kase.stdlib.createSafely
import com.squareup.anvil.compiler.api.GeneratedFileWithSources
import io.kotest.matchers.file.shouldExist
import io.kotest.matchers.shouldBe
import java.io.File

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

  val binaryFile = workingDir.resolve("cache.bin")

  val projectDir = ProjectDir(workingDir)
  val generatedDir = workingDir.resolve("generated")

  val cache = GeneratedFileCache.fromFile(
    binaryFile = binaryFile,
    projectDir = projectDir,
  )
  val fileOperations = FileCacheOperations(
    cache = cache,
    projectDir = projectDir,
  )

  val source1 by lazy(LazyThreadSafetyMode.NONE) { sourceFile("source1.kt") }
  val source2 by lazy(LazyThreadSafetyMode.NONE) { sourceFile("source2.kt") }
  val source3 by lazy(LazyThreadSafetyMode.NONE) { sourceFile("source3.kt") }
  val source4 by lazy(LazyThreadSafetyMode.NONE) { sourceFile("source4.kt") }
  val source5 by lazy(LazyThreadSafetyMode.NONE) { sourceFile("source5.kt") }

  val gen1 by lazy(LazyThreadSafetyMode.NONE) { generatedFile("generated1.kt") }
  val gen2 by lazy(LazyThreadSafetyMode.NONE) { generatedFile("generated2.kt") }
  val gen3 by lazy(LazyThreadSafetyMode.NONE) { generatedFile("generated3.kt") }
  val gen4 by lazy(LazyThreadSafetyMode.NONE) { generatedFile("generated4.kt") }
  val gen5 by lazy(LazyThreadSafetyMode.NONE) { generatedFile("generated5.kt") }
  val gen6 by lazy(LazyThreadSafetyMode.NONE) { generatedFile("generated6.kt") }
  val gen7 by lazy(LazyThreadSafetyMode.NONE) { generatedFile("generated7.kt") }
  val gen8 by lazy(LazyThreadSafetyMode.NONE) { generatedFile("generated8.kt") }
  val gen9 by lazy(LazyThreadSafetyMode.NONE) { generatedFile("generated9.kt") }

  val RelativeFile.absolute: AbsoluteFile
    get() = absolute(projectDir)

  val GeneratedFileWithSources.relativeFile: RelativeFile
    get() = AbsoluteFile(file).relativeTo(projectDir)

  val FileType.absoluteFile: File
    get() = when (this) {
      is AbsoluteFile -> file
      is RelativeFile -> workingDir.resolve(file)
    }

  fun currentFiles(): List<RelativeFile> = workingDir.walkTopDown()
    .filter { it.isFile && it != binaryFile }
    .map { it.relativeTo(workingDir) }
    .sorted()
    .map {
      when {
        it.startsWith("src") -> RelativeFile(it)
        else -> RelativeFile(it)
      }
    }
    .toList()

  private fun sourceFile(name: String): RelativeFile =
    AbsoluteFile(workingDir.resolve("src/$name"))
      .also { it.file.createSafely("content for $name") }
      .relativeTo(projectDir)

  private fun generatedFile(
    path: String,
    content: String = "content for $path",
  ): GeneratedFileWithSources = GeneratedFileWithSources(
    file = generatedDir.resolve(path).createSafely(content = content),
    content = content,
    sourceFiles = emptySet(),
  )

  fun RelativeFile.writeText(content: String) {
    absoluteFile.createSafely(content = content)
  }

  fun GeneratedFileCache.addAll(vararg generated: GeneratedFileWithSources) {
    for (gen in generated) {
      addGeneratedFile(gen)
    }
  }

  fun GeneratedFileWithSources.toSourceFile(): RelativeFile {
    return AbsoluteFile(file).relativeTo(projectDir)
  }

  fun <T> GeneratedFileWithSources.withSources(vararg sources: T): GeneratedFileWithSources
    // NB: `T` can only be a `RelativeFile`,
    // but the compiler doesn't support vararg with an explicit JvmInline value class,
    // due to the inline mangling.
    where T : FileType {
    return GeneratedFileWithSources(
      file = file,
      content = content,
      sourceFiles = sourceFiles + sources.map { it.absoluteFile },
    )
  }

  fun FileCacheOperations.addToCache(vararg gens: GeneratedFileWithSources) {
    addToCache(emptyList(), gens.toList())
  }

  fun newCache(binaryFile: File = this.binaryFile): GeneratedFileCache {
    return GeneratedFileCache.fromFile(
      binaryFile = binaryFile,
      projectDir = projectDir,
    )
  }

  infix fun File.shouldExistWithText(expectedText: String) {
    shouldExist()
    readText() shouldBe expectedText
  }

  class Factory : NoParamTestEnvironmentFactory<CacheTestEnvironment> {
    override fun create(
      names: List<String>,
      location: TestLocation,
    ): CacheTestEnvironment = CacheTestEnvironment(names = names, testLocation = location)
  }
}
