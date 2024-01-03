package com.squareup.anvil.compiler.api

import java.io.File

/**
 * Represents a generated file that Anvil should eventually write. The [sourceFiles] are the
 * files that triggered the generation of this file or are referenced by the generated file.
 * A modification to any of the [sourceFiles] will invalidate this generated file.
 *
 * All source files must be:
 *   - absolute paths
 *   - actual files (not directories)
 *   - existent in the file system
 *
 * @property file the [File] to generate.
 * @property content the file contents to write to [file].
 * @property sourceFiles the source files used to generate this file.
 * @throws AnvilCompilationException if a [sourceFiles] is not an absolute file, doesn't exist, or is a directory
 * @throw AnvilCompilationException if a source file is the same as the generated [file]
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
          |$file
          |
          """.trimMargin(),
        )
      }
    }
    require(sourceFiles.none { it == file }) {
      """
      |${this::class.simpleName} must not contain the generated file as a source file.
      |
      |source files:
      |${sourceFiles.sorted().joinToString("\n")}
      """.trimMargin()
    }
    require(sourceFiles.all { it.isFile && it.isAbsolute }) {

      val notAbsolute = sourceFiles.filterNot { it.isAbsolute }.sorted()
      val notFiles = sourceFiles.filterNot { it.isFile }.sorted()

      buildString {
        appendLine(
          """
          All source files must be:
            - absolute paths
            - actual files (not directories)
            - existent in the file system
          """.trimIndent(),
        )
        if (notAbsolute.isNotEmpty()) {
          append(
            """
            |
            |not absolute:
            |${notAbsolute.joinToString("\n")}
            """.trimMargin(),
          )
        }
        if (notFiles.isNotEmpty()) {
          append(
            """
            |
            |not files:
            |${notFiles.joinToString("\n")}
            """.trimMargin(),
          )
        }
      }
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
    appendLine("  file: $file")
    appendLine("  sourceFiles: ${sourceFiles.sorted()}")
    appendLine("  content: $content")
    appendLine(")")
  }
}
