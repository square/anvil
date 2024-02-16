package com.squareup.anvil.compiler.codegen.incremental

import java.io.File
import java.io.Serializable

@JvmInline
internal value class ProjectDir(val file: File) : Serializable {
  fun resolve(relativePath: RelativeFile): AbsoluteFile {
    return AbsoluteFile(file.resolve(relativePath.file))
  }

  override fun toString(): String = "ProjectDir: $file"
}

/** Type-safe files to simplify caching logic. */
internal sealed interface FileType : Serializable, Comparable<FileType> {
  val file: File
  override fun compareTo(other: FileType): Int = file.compareTo(other.file)
}

/**
 * All cached files must be relative to work with Gradle remote build caches.
 * They are all relative to the [project directory][ProjectDir],
 * which mirrors what Gradle does with relative file inputs to tasks.
 */
@JvmInline
internal value class RelativeFile(override val file: File) : FileType {

  fun absolute(projectDir: ProjectDir): AbsoluteFile {
    return AbsoluteFile(projectDir.file.resolve(file))
  }

  fun exists(projectDir: ProjectDir): Boolean = absolute(projectDir).exists()

  override fun toString(): String = "RelativeFile: $file"

  companion object {
    /**
     * A special case "source file" placeholder that matches any source file.
     * This is used when a code generator supports
     * [tracking source files][com.squareup.anvil.compiler.api.AnvilContext.trackSourceFiles],
     * but the source list for some file is empty.
     *
     * [FileCacheOperations] will treat `ANY` as "changed"
     * for any change to the compilation source code,
     * and will delete all generated files with it as a source file.
     */
    val ANY = RelativeFile(File("<any source file>"))
  }
}

/**
 * Input `KtFile` files have absolute paths, as do the files
 *   in [GeneratedFileWithSources][com.squareup.anvil.compiler.api.GeneratedFileWithSources].
 * All normal file I/O operations must use the absolute paths.
 */
@JvmInline
internal value class AbsoluteFile(override val file: File) : FileType {
  fun exists(): Boolean = file.exists()
  fun relativeTo(projectDir: ProjectDir): RelativeFile {
    return RelativeFile(file.relativeTo(projectDir.file))
  }

  override fun toString(): String = "AbsoluteFile: $file"
}
