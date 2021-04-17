package com.squareup.anvil.compiler.codegen

import com.squareup.anvil.compiler.codegen.CodeGenerator.GeneratedFile
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

internal interface CodeGenerator {

  /**
   * Called multiple times in order to create new code. Note that instances should not rely on
   * being able to resolve [projectFiles] to descriptors. They should rather use the Psi APIs to
   * parse files.
   *
   * In the first round code generators are called with the files from the project. In following
   * rounds [projectFiles] represents files that were generated by code generators until no
   * code generator produces any files anymore.
   */
  fun generateCode(
    codeGenDir: File,
    module: ModuleDescriptor,
    projectFiles: Collection<KtFile>
  ): Collection<GeneratedFile>

  /**
   * Called after the last round of [generateCode] when no new files were produced by any code
   * generator anymore. The returned result should contain files that were added or changed one
   * last time. Code generates that do not impact other code generators get a last chance to
   * evaluate these results.
   */
  fun flush(
    codeGenDir: File,
    module: ModuleDescriptor
  ): Collection<GeneratedFile> = emptyList()

  data class GeneratedFile(
    val file: File,
    val content: String
  )
}

/**
 * Write [content] into a new file for the given [packageName] and [fileName]. [fileName] usually
 * refers to the class name.
 */
@Suppress("unused")
internal fun CodeGenerator.createGeneratedFile(
  codeGenDir: File,
  packageName: String,
  fileName: String,
  content: String
): GeneratedFile {
  val directory = File(codeGenDir, packageName.replace('.', File.separatorChar))
  val file = File(directory, "$fileName.kt")
  check(file.parentFile.exists() || file.parentFile.mkdirs()) {
    "Could not generate package directory: ${file.parentFile}"
  }
  file.writeText(
    content
      .also(::println)
  )

  return GeneratedFile(file, content)
}
