package com.squareup.anvil.compiler.api

import java.io.File

/**
 * Represents a generated file that Anvil should eventually write.
 *
 * @property file the [File] to generate.
 * @property content the file contents to write to [file].
 */
public data class GeneratedFile(
  val file: File,
  val content: String,
)
