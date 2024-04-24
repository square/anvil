package com.squareup.anvil.compiler.codegen

import com.squareup.anvil.compiler.api.FileWithContent
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

/**
 * A [PrivateCodeGenerator] that doesn't generate any code, but instead just performs checks.
 */
internal abstract class CheckOnlyCodeGenerator : PrivateCodeGenerator() {

  override val group: Int get() = Int.MAX_VALUE

  final override fun generateCodePrivate(
    codeGenDir: File,
    module: ModuleDescriptor,
    projectFiles: Collection<KtFile>,
  ): Collection<FileWithContent> {
    checkCode(codeGenDir, module, projectFiles)
    return emptyList()
  }

  protected abstract fun checkCode(
    codeGenDir: File,
    module: ModuleDescriptor,
    projectFiles: Collection<KtFile>,
  )
}
