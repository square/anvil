package com.squareup.anvil.compiler.codegen

import com.squareup.anvil.compiler.api.CodeGenerator
import com.squareup.anvil.compiler.api.FileWithContent
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

/**
 * Generates code that doesn't impact any other [CodeGenerator], meaning no other code generator
 * will process the generated code produced by this instance.
 */
internal abstract class PrivateCodeGenerator : CodeGenerator {

  override val group: Int get() = 10

  final override fun generateCode(
    codeGenDir: File,
    module: ModuleDescriptor,
    projectFiles: Collection<KtFile>,
  ): Collection<FileWithContent> = generateCodePrivate(codeGenDir, module, projectFiles)

  protected abstract fun generateCodePrivate(
    codeGenDir: File,
    module: ModuleDescriptor,
    projectFiles: Collection<KtFile>,
  ): Collection<FileWithContent>
}
