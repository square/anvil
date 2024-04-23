package com.squareup.anvil.compiler.codegen.incremental

import java.io.File
import java.io.Serializable

/**
 * Input `KtFile` files have absolute paths, as do the files
 *   in [GeneratedFileWithSources][com.squareup.anvil.compiler.api.GeneratedFileWithSources].
 * All normal file I/O operations must use the absolute paths.
 */
@JvmInline
internal value class AbsoluteFile(val file: File) : Serializable, Comparable<AbsoluteFile> {

  fun exists(): Boolean = file.exists()

  override fun compareTo(other: AbsoluteFile): Int = file.compareTo(other.file)

  override fun toString(): String = "AbsoluteFile: $file"

  companion object {
    /**
     * A special case "source file" placeholder that matches any source file.
     * This is used when a code generator supports
     * [tracking source files][com.squareup.anvil.compiler.api.AnvilContext.trackSourceFiles],
     * but the source list for some file is empty.
     *
     * [FileCacheOperations] will treat `ANY_ABSOLUTE` as "changed"
     * for any change to the compilation source code,
     * and will delete all generated files with it as a source file.
     */
    val ANY_ABSOLUTE = AbsoluteFile(File("<any source file>"))
  }
}
