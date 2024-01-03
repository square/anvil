package com.squareup.anvil.compiler.codegen.incremental

import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.api.GeneratedFileWithSources
import java.io.Serializable

internal class FileCacheOperations(
  internal val cache: GeneratedFileCache,
  private val projectDir: ProjectDir,
) : Serializable {

  fun addToCache(
    sourceFiles: List<AbsoluteFile>,
    filesWithSources: Collection<GeneratedFileWithSources>,
  ) {

    cache.use { cache ->

      // Cache the MD5 hashes of all source files
      // so that we can do our own change detection in future runs.
      // We do this because the Kotlin "files" inputs aren't incremental,
      // and we need to know for sure whether a file has changed,
      // even if it wasn't previously used as a source file for a generated file.
      for (sourceFile in sourceFiles) {
        cache.addSourceFile(sourceFile.relativeTo(projectDir))
      }

      for (generatedFile in filesWithSources) {
        cache.addGeneratedFile(generatedFile)
      }
    }
  }

  /**
   * @param inputSourceFiles The source files that have changed since the last compilation.
   */
  fun restoreFromCache(inputSourceFiles: List<AbsoluteFile>) {

    val (changedInput, unchangedInput) = inputSourceFiles.partition { cache.hasChanged(it) }

    val changedSource = changedInput
      .mapTo(mutableSetOf()) { it.relativeTo(projectDir) }

    // Source files that don't have any source files of their own.
    val rootSourceFiles = cache.sourceFiles
      .filter { source ->
        source != RelativeFile.ANY &&
          cache.getSourceFiles(source).isEmpty()
      }

    // Root source files aren't generated,
    // so if they don't exist, that means they were deleted intentionally.
    val deletedSources = rootSourceFiles.filter { !it.exists(projectDir) }

    val deletedOrChanged = deletedSources + changedSource

    // All generated files that are downstream of changed or deleted source files.
    // For example, if `Source.kt` triggered the generation of `GenA.kt`
    // and `GenA.kt` triggered the generation of `GenB.kt`, then removing `Source.kt` should
    // invalidate `GenA.kt` and `GenB.kt`.
    val toInvalidate = deletedOrChanged
      // Add the ANY token here so that anything using it as a source file is invalidated.
      .plus(RelativeFile.ANY)
      .flatMap(cache::getGeneratedFilesRecursive)
      .plus(deletedOrChanged)
      .toSet()

    // For each invalid file:
    // - remove it from the cache
    // - delete any generated files that claim it as a source
    for (file in toInvalidate) {
      cache.removeSource(file)

      // Delete generated files, not source files.
      if (cache.isGenerated(file)) {
        val abs = file.absolute(projectDir).file
        if (abs.exists()) {

          if (inputSourceFiles.toSet().contains(file.absolute(projectDir))) {
            throw AnvilCompilationException(
              "Generated file ${file.absolute(projectDir)} was marked as changed, but it was " +
                "also marked as a source file. This is a bug in Anvil. Please report it.",
            )
          }

          if (!abs.deleteRecursively() && abs.exists()) {
            throw AnvilCompilationException("Could not delete file: $abs")
          }
        }
      }
    }

    val unchangedGenerated =
      unchangedInput.flatMapTo(mutableSetOf()) { cache.getGeneratedFilesRecursive(it) }

    for (gen in unchangedGenerated) {
      val file = projectDir.resolve(gen).file
      // `File.isFile` implies that the file exists as well. We will write the file if nothing is
      // there, and we will overwrite the file if it exists but is a directory.
      if (!file.isFile) {
        file.parentFile.mkdirs()
        file.createNewFile()
        file.writeText(cache.getContent(gen))
      }
    }
  }
}
