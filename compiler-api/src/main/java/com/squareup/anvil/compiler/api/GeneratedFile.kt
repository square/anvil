package com.squareup.anvil.compiler.api

import java.io.File

/**
 * Represents a generated file that Anvil should eventually write.
 *
 * @property file the [File] to generate.
 * @property content the file contents to write to [file].
 */
@Deprecated(
  message = "Use GeneratedFileWithSources instead.",
  replaceWith = ReplaceWith(
    expression = "GeneratedFileWithSources(file, content, setOf(TODO(\"Add some source files using <somePsiElement>.containingFileAsJavaFile()\")))",
    imports = [
      "com.squareup.anvil.compiler.api.GeneratedFileWithSources",
      "com.squareup.anvil.compiler.internal.containingFileAsJavaFile",
    ],
  ),
  level = DeprecationLevel.WARNING,
)
public data class GeneratedFile(
  override val file: File,
  override val content: String,
) : FileWithContent

/** Represents a generated file that Anvil should eventually write. */
public sealed interface FileWithContent : Comparable<FileWithContent> {
  /** The file to generate. */
  public val file: File

  /** The text to write to [file]. */
  public val content: String

  public operator fun component1(): File = file
  public operator fun component2(): String = content

  override fun compareTo(other: FileWithContent): Int = file.compareTo(other.file)
}
