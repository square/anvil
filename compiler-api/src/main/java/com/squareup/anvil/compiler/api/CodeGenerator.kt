package com.squareup.anvil.compiler.api

import com.squareup.anvil.annotations.ExperimentalAnvilApi
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

/**
 * This interface allows for easy extension of Anvil's core compiler functionality for cases where
 * you need to generate extra code during compilation.
 *
 * This interface is loaded via [java.util.ServiceLoader] and you should package a service file for
 * your implementations accordingly. As such, order of execution is **not** guaranteed.
 *
 * A [CodeGenerator] can generate arbitrary Kotlin code, whether it be for Anvil or Dagger or both!
 * Note that the generated code _must_ be Kotlin code. Java, arbitrary resource files, etc are not
 * supported.
 */
@ExperimentalAnvilApi
public interface CodeGenerator : AnvilApplicabilityChecker {

  /**
   * Returns true if this code generator is applicable for the given [context] or false if not. This
   * will only be called _once_.
   */
  public override fun isApplicable(context: AnvilContext): Boolean

  /**
   * Called multiple times in order to create new code. Note that instances should not rely on
   * being able to resolve [projectFiles] to descriptors. They should rather use the Psi APIs to
   * parse files.
   *
   * In the first round code generators are called with the files from the project. In following
   * rounds [projectFiles] represents files that were generated by code generators until no
   * code generator produces any files anymore.
   */
  public fun generateCode(
    codeGenDir: File,
    module: ModuleDescriptor,
    projectFiles: Collection<KtFile>
  ): Collection<GeneratedFile>
}

/**
 * Write [content] into a new file for the given [packageName] and [fileName]. [fileName] usually
 * refers to the class name.
 */
@ExperimentalAnvilApi
@Suppress("unused")
public fun CodeGenerator.createGeneratedFile(
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
  file.writeText(content)

  return GeneratedFile(file, content)
}
