package com.squareup.anvil.compiler.codegen

import com.squareup.anvil.compiler.api.CodeGenerator
import com.squareup.anvil.compiler.api.GeneratedFile
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

/**
 * Generates code that doesn't impact any other [CodeGenerator], meaning no other code generator
 * will process the generated code produced by this instance. A [PrivateCodeGenerator] is called
 * one last time after [FlushingCodeGenerator.flush] has been called to get a chance to evaluate
 * written results.
 */
internal abstract class PrivateCodeGenerator : CodeGenerator {
  final override fun generateCode(
    codeGenDir: File,
    module: ModuleDescriptor,
    projectFiles: Collection<KtFile>,
  ): Collection<GeneratedFile> {
    generateCodePrivate(codeGenDir, module, projectFiles)
    return emptyList()
  }

  protected abstract fun generateCodePrivate(
    codeGenDir: File,
    module: ModuleDescriptor,
    projectFiles: Collection<KtFile>,
  )
}
