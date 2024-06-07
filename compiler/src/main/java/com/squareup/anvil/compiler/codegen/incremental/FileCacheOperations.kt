package com.squareup.anvil.compiler.codegen.incremental

import com.squareup.anvil.compiler.api.FileWithContent
import com.squareup.anvil.compiler.api.GeneratedFileWithSources
import com.squareup.anvil.compiler.mapToSet
import com.squareup.anvil.compiler.requireDelete
import java.io.File
import java.io.Serializable

internal class FileCacheOperations(
  internal val cache: GeneratedFileCache,
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
        cache.addSourceFile(sourceFile)
      }

      for (generatedFile in filesWithSources) {
        cache.addGeneratedFile(generatedFile)
      }
    }
  }

  /**
   * For any non-generated file in the cache that no longer exists on disk,
   * we delete any downstream generated files from the disk.
   *
   * The [inputKtFiles] parameter should correspond to the `files: List<KtFile>` parameters in
   * [doAnalysis][com.squareup.anvil.compiler.codegen.CodeGenerationExtension.doAnalysis].
   *
   * For any file in [inputKtFiles], we delete it and everything generated from it from the cache,
   * and we delete anything generated from it from the disk.
   * This is because we'll be passing those files through the code generators again anyway,
   * so we'll get up-to-date results. This is also useful when a file's content is unchanged,
   * but there was a classpath change that would result in different bindings or interface merges.
   *
   * For any non-generated file on disk that was *not* passed in as `inputKtFiles`
   * *and* is identical to its version in the cache,
   * we restore any downstream generated files from the cache to the disk.
   *
   * We use all cached source files as the basis for cache restoration,
   * instead of using the [inputKtFiles] passed in by the compiler.
   * Those `inputKtFiles` are an incremental, possibly partial list of source files.
   * If a source file exists on disk but isn't in the list,
   * that means the compiler expects it and any generated files to be up-to-date --
   * so that's what we need to restore.
   */
  fun restoreFromCache(generatedDir: File, inputKtFiles: Set<AbsoluteFile>): RestoreCacheResult {

    // Files that weren't generated.
    val rootSourceFiles = cache.rootSourceFiles

    val (changedSourceFiles, unchangedSourceFiles) = rootSourceFiles
      .partition {
        // We treat anything in `inputKtFiles` as changed.
        // If our cache is somehow out of sync with the file's content,
        // then we can't trust anything else in the cache related to this file either.
        it in inputKtFiles || cache.hasChanged(it)
      }

    val deletedInvalid = deleteInvalidFiles(
      rootSourceFiles = rootSourceFiles,
      changedSourceFiles = changedSourceFiles,
    )

    val restoredFiles = unchangedSourceFiles
      .flatMapTo(mutableSetOf()) { cache.getGeneratedFilesRecursive(it) }
      .map { absolute ->
        val file = absolute.file

        if (!file.isFile) {
          file.parentFile.mkdirs()
          file.createNewFile()
        }

        val cachedContent = cache.getContent(absolute)
        file.writeText(cachedContent)

        GeneratedFileWithSources(
          file = file,
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
    val validFilesAsFiles = validGeneratedFiles.mapToSet { it.file }

    return generatedDir.walkBottomUp()
      .filter { it.isFile }
      .filter { it !in validFilesAsFiles }
      .onEach { it.requireDelete() }
      .mapTo(mutableListOf(), ::AbsoluteFile)
  }

  private fun deleteInvalidFiles(
    rootSourceFiles: Iterable<AbsoluteFile>,
    changedSourceFiles: List<AbsoluteFile>,
  ): List<AbsoluteFile> {
    // Root source files aren't generated,
    // so if they don't exist, that means they were deleted intentionally.
    val deletedSources = rootSourceFiles.filter { !it.exists() }

    val deletedOrChanged = deletedSources + changedSourceFiles

    // All generated files that are downstream of changed or deleted source files.
    // For example, if `Source.kt` triggered the generation of `GenA.kt`
    // and `GenA.kt` triggered the generation of `GenB.kt`, then removing `Source.kt` should
    // invalidate `GenA.kt` and `GenB.kt`.
    // Add the `ANY_ABSOLUTE` token here so that anything using it as a source file is invalidated.
    val invalidSource = deletedOrChanged + AbsoluteFile.ANY_ABSOLUTE

    return invalidSource
      .plus(invalidSource.flatMap { cache.getGeneratedFilesRecursive(it) })
      .mapNotNull { invalid ->

        val isSourceFile = rootSourceFiles.contains(invalid)

        cache.removeSource(invalid)

        if (isSourceFile) return@mapNotNull null

        // If the file is generated and it exists on the disk, delete it.
        invalid.takeIf { it.exists() }
          ?.also { it.file.requireDelete() }
      }
  }

  internal data class RestoreCacheResult(
    val restoredFiles: List<FileWithContent>,
    val deletedFiles: List<AbsoluteFile>,
  )
}
