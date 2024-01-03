package com.squareup.anvil.compiler.api

import java.io.File

/**
 * Represents a generated file that Anvil should eventually write. The [sourceFiles] are the
 * files that triggered the generation of this file or are referenced by the generated file.
 * A modification to any of the [sourceFiles] will invalidate this generated file.
 *
 * @property file the [File] to generate.
 * @property content the file contents to write to [file].
 * @property sourceFiles the source files used to generate this file.
 */
public class GeneratedFileWithSources(
  override val file: File,
  override val content: String,
  public val sourceFiles: Set<File>,
) : FileWithContent {

  init {
    fun require(condition: Boolean, lazyMessage: () -> String) {
      if (!condition) {
        throw AnvilCompilationException(
          """
          |message:
          |${lazyMessage()}
          |
          |generated file:
          |$this
          """.trimMargin(),
        )
      }
    }
    require(sourceFiles.none { it == file }) {
      "GeneratedFileWithSources must not contain the generated file as a source file."
    }
    require(sourceFiles.none { it.isAbsolute && !it.exists() }) {
      "If a source file is also a generated file, its path should be relative.\n" +
        sourceFiles.joinToString("\n")
    }
  }

  public operator fun component3(): Set<File> = sourceFiles

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GeneratedFileWithSources) return false

    if (file != other.file) return false
    if (content != other.content) return false
    if (sourceFiles != other.sourceFiles) return false

    return true
  }

  override fun hashCode(): Int {
    var result = file.hashCode()
    result = 31 * result + content.hashCode()
    result = 31 * result + sourceFiles.hashCode()
    return result
  }

  override fun toString(): String = buildString {
    appendLine("GeneratedFileWithSource(")
    appendLine("  file: $file,")
    appendLine("  content: '$content',")
    appendLine("  sourceFiles: $sourceFiles")
    appendLine(")")
  }
}
