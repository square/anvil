package com.squareup.anvil.compiler.internal.testing

import com.squareup.anvil.compiler.api.AnvilContext
import com.squareup.anvil.compiler.api.CodeGenerator
import com.squareup.anvil.compiler.api.GeneratedFile
import com.squareup.anvil.compiler.api.createGeneratedFile
import com.squareup.anvil.compiler.internal.reference.ClassReference
import com.squareup.anvil.compiler.internal.reference.classAndInnerClassReferences
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

private var counter = 0

fun simpleCodeGenerator(
  mapper: CodeGenerator.(clazz: ClassReference.Psi) -> String?
): CodeGenerator = object : CodeGenerator {
  override fun isApplicable(context: AnvilContext): Boolean = true

  override fun generateCode(
    codeGenDir: File,
    module: ModuleDescriptor,
    projectFiles: Collection<KtFile>
  ): Collection<GeneratedFile> {
    return projectFiles
      .classAndInnerClassReferences(module)
      .mapNotNull {
        mapper.invoke(this, it)
      }
      .map { content ->
        val packageName = content.lines()
          .map { it.trim() }
          .firstNotNullOfOrNull { line ->
            line
              .takeIf { it.startsWith("package ") }
              ?.substringAfter("package ")
          }
          ?: "tempPackage"

        val fileName = content.lines()
          .map { it.trim() }
          .firstNotNullOfOrNull { line ->
            // Try finding the class name.
            line
              .takeIf { it.startsWith("class ") || it.contains(" class ") }
              ?.substringAfter("class ")
              ?.trim()
              ?.substringBefore(" ")
              ?: line
                // Check for interfaces, too.
                .takeIf { it.startsWith("interface ") || it.contains(" interface ") }
                ?.substringAfter("interface ")
                ?.trim()
                ?.substringBefore(" ")
          }
          ?: "NewFile${counter++}"

        createGeneratedFile(
          codeGenDir = codeGenDir,
          packageName = packageName,
          fileName = fileName,
          content = content
        )
      }
      .toList()
  }
}
