package com.squareup.anvil.compiler.codegen

import com.squareup.anvil.compiler.AnvilCompilationException
import com.squareup.anvil.compiler.HINT_BINDING_PACKAGE_PREFIX
import com.squareup.anvil.compiler.codegen.CodeGenerator.GeneratedFile
import com.squareup.anvil.compiler.contributesBindingFqName
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifierTypeOrDefault
import java.io.File

/**
 * Generates a hint for each contributed class in the `anvil.hint.bindings` package. This allows
 * the compiler plugin to find all contributed bindings a lot faster when merging modules and
 * component interfaces.
 */
internal class ContributesBindingGenerator : CodeGenerator {
  override fun generateCode(
    codeGenDir: File,
    module: ModuleDescriptor,
    projectFiles: Collection<KtFile>
  ): Collection<GeneratedFile> {
    return projectFiles.asSequence()
        .flatMap { it.classesAndInnerClasses() }
        .filter { it.hasAnnotation(contributesBindingFqName) }
        .onEach { clazz ->
          if (clazz.visibilityModifierTypeOrDefault().value != KtTokens.PUBLIC_KEYWORD.value) {
            throw AnvilCompilationException(
                "${clazz.requireFqName()} is binding a type, but the class is not public. Only " +
                    "public types are supported.",
                element = clazz.identifyingElement
            )
          }
        }
        .map { clazz ->
          val packageName = clazz.containingKtFile.packageFqName.asString()
          val generatedPackage = "$HINT_BINDING_PACKAGE_PREFIX.$packageName"
          val className = clazz.requireFqName()
              .asString()

          val directory = File(codeGenDir, generatedPackage.replace('.', File.separatorChar))
          val file = File(directory, "${className.replace('.', '_')}.kt")
          check(file.parentFile.exists() || file.parentFile.mkdirs()) {
            "Could not generate package directory: $this"
          }

          val content = """
            package $generatedPackage
            
            val ${className.replace('.', '_')} = $className::class
          """.trimIndent()
          file.writeText(content)

          GeneratedFile(file, content)
        }
        .toList()
  }
}
