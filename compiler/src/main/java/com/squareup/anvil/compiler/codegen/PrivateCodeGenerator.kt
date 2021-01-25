package com.squareup.anvil.compiler.codegen

import com.squareup.anvil.compiler.codegen.CodeGenerator.GeneratedFile
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

/**
 * Generates code that doesn't impact any other [CodeGenerator], meaning no other code generator
 * will process the generated code produced by this instance. A [PrivateCodeGenerator] is called
 * one last time after [flush] has been called to get a chance to evaluate written results.
 */
internal abstract class PrivateCodeGenerator : CodeGenerator {
  final override fun generateCode(
    codeGenDir: File,
    module: ModuleDescriptor,
    projectFiles: Collection<KtFile>
  ): Collection<GeneratedFile> {
    generateCodePrivate(codeGenDir, module, projectFiles)
    return emptyList()
  }

  protected abstract fun generateCodePrivate(
    codeGenDir: File,
    module: ModuleDescriptor,
    projectFiles: Collection<KtFile>
  )

  /**
   * Write [content] into a new file for the given [packageName] and [className].
   */
  protected fun createGeneratedFile(
    codeGenDir: File,
    packageName: String,
    className: String,
    content: String
  ): GeneratedFile {
    val directory = File(codeGenDir, packageName.replace('.', File.separatorChar))
    val file = File(directory, "$className.kt")
    check(file.parentFile.exists() || file.parentFile.mkdirs()) {
      "Could not generate package directory: ${file.parentFile}"
    }
    file.writeText(content)

    return GeneratedFile(file, content)
  }
}
