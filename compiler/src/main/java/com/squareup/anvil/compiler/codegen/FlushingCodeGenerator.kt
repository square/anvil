package com.squareup.anvil.compiler.codegen

import com.squareup.anvil.compiler.api.CodeGenerator
import com.squareup.anvil.compiler.api.GeneratedFileWithSources
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import java.io.File

internal interface FlushingCodeGenerator : CodeGenerator {

  /**
   * Called after the last round of [generateCode] when no new files were produced by any code
   * generator anymore. The returned result should contain files that were added or changed one
   * last time. Code generates that do not impact other code generators get a last chance to
   * evaluate these results.
   */
  fun flush(
    codeGenDir: File,
    module: ModuleDescriptor,
  ): Collection<GeneratedFileWithSources>
}
