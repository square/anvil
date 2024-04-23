package com.squareup.anvil.compiler.codegen.incremental

import java.io.File
import java.io.Serializable

internal sealed interface BaseDir : Serializable {
  val file: File

  @JvmInline
  value class ProjectDir(override val file: File) : BaseDir {
    override fun toString(): String = "ProjectDir: $file"
  }

  @JvmInline
  value class BuildDir(override val file: File) : BaseDir {
    override fun toString(): String = "BuildDir: $file"
  }
}
