package com.squareup.anvil.compiler.codegen.incremental

import com.squareup.anvil.compiler.api.FileWithContent
import com.squareup.anvil.compiler.api.GeneratedFileWithSources
import com.squareup.anvil.compiler.requireDelete
import java.io.File
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
   * For any non-generated file in the cache that no longer exists on disk,
   * delete any downstream generated files from the disk.
   *
   * For any non-generated file on disk that is identical to its version in the cache,
   * restore any downstream generated files from the cache to the disk.
   *
   * We use all cached source files as the basis for cache restoration,
   * instead of using the `List<KtFile>` passed in by the compiler.
   * The file list from the compiler can be incremental, not including all extant source files.
   * With the incomplete list, any missing files
   * that were generated from an unchanged source file would not be restored.
   */
  fun restoreFromCache(generatedDir: File): RestoreCacheResult {

    // Files that weren't generated.
    val rootSourceFiles = cache.rootSourceFiles

    val (changedSourceFiles, unchangedSourceFiles) = rootSourceFiles
      .partition { cache.hasChanged(it) }

    val deletedInvalid = deleteInvalidFiles(
      rootSourceFiles = rootSourceFiles,
      changedSourceFiles = changedSourceFiles,
    )

    val restoredFiles = unchangedSourceFiles
      .flatMapTo(mutableSetOf()) { cache.getGeneratedFilesRecursive(it) }
      .map { relative ->
        val absolute = projectDir.resolve(relative).file

        if (!absolute.isFile) {
          absolute.parentFile.mkdirs()
          absolute.createNewFile()
        }

        val cachedContent = cache.getContent(relative)
        absolute.writeText(cachedContent)

        GeneratedFileWithSources(
          file = absolute,
          content = cachedContent,
          sourceFiles = emptySet(),
        )
      }

    val deletedUntracked = deleteUntrackedFiles(
      generatedDir = generatedDir,
      validGeneratedFiles = restoredFiles,
    )

    return RestoreCacheResult(
      restoredFiles = restoredFiles,
      deletedFiles = deletedInvalid + deletedUntracked,
    )
  }

  /**
   * If there are any files in the generated directory that aren't valid files
   * (derived from current, unchanged source files), delete them.
   * They aren't tracked in the cache, or they would have been deleted already.
   *
   * This should theoretically never happen because of how Gradle treats
   * the generated "output" directory, but that's technically not a guarantee.
   */
  private fun deleteUntrackedFiles(
    generatedDir: File,
    validGeneratedFiles: Collection<FileWithContent>,
  ): List<AbsoluteFile> {
    val validFilesAsFiles = validGeneratedFiles.mapTo(mutableSetOf()) { it.file }

    return generatedDir.walkBottomUp()
      .filter { it.isFile }
      .filter { it !in validFilesAsFiles }
      .onEach { it.requireDelete() }
      .mapTo(mutableListOf()) { AbsoluteFile(it) }
  }

  private fun deleteInvalidFiles(
    rootSourceFiles: Iterable<RelativeFile>,
    changedSourceFiles: List<RelativeFile>,
  ): List<AbsoluteFile> {
    // Root source files aren't generated,
    // so if they don't exist, that means they were deleted intentionally.
    val deletedSources = rootSourceFiles.filter { !it.exists(projectDir) }

    val deletedOrChanged = deletedSources + changedSourceFiles

    // All generated files that are downstream of changed or deleted source files.
    // For example, if `Source.kt` triggered the generation of `GenA.kt`
    // and `GenA.kt` triggered the generation of `GenB.kt`, then removing `Source.kt` should
    // invalidate `GenA.kt` and `GenB.kt`.
    // Add the `ANY` token here so that anything using it as a source file is invalidated.
    val invalidSource = deletedOrChanged + RelativeFile.ANY

    return invalidSource
      .plus(invalidSource.flatMap { cache.getGeneratedFilesRecursive(it) })
      .mapNotNull { invalid ->

        val isSourceFile = rootSourceFiles.contains(invalid)

        cache.removeSource(invalid)

        if (isSourceFile) return@mapNotNull null

        if (!invalid.exists(projectDir)) return@mapNotNull null

        // If the file is generated and it exists on the disk, delete it.
        invalid.absolute(projectDir).also { it.file.requireDelete() }
      }
  }

  internal data class RestoreCacheResult(
    val restoredFiles: List<FileWithContent>,
    val deletedFiles: List<AbsoluteFile>,
  )
}
