package com.squareup.anvil.compiler.api

import com.google.auto.service.AutoService
import com.squareup.anvil.compiler.internal.reference.classAndInnerClassReferences
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

@AutoService(CodeGenerator::class)
class CustomCodeGeneratorSample : CodeGenerator {

  override fun isApplicable(context: AnvilContext): Boolean = true

  override fun generateCode(
    codeGenDir: File,
    module: ModuleDescriptor,
    projectFiles: Collection<KtFile>,
  ): Collection<FileWithContent> {
    return projectFiles.classAndInnerClassReferences(module)
      .filter { it.isAnnotatedWith(FqName("sample.MyAnnotation")) }
      .map { clazz ->
        createGeneratedFile(
          codeGenDir,
          packageName = clazz.packageFqName.asString(),
          fileName = "MyGeneratedClass",
          content = """
            package ${clazz.packageFqName}
            
            class MyGeneratedClass {
              fun doSomething() {
                println("Hello, World!")
              }
            }
          """.trimIndent(),
          sourceFile = clazz.containingFileAsJavaFile,
        )
      }
      .toList()
  }
}
