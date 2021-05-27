package com.squareup.anvil.test

import com.google.auto.service.AutoService
import com.squareup.anvil.compiler.api.AnvilContext
import com.squareup.anvil.compiler.api.CodeGenerator
import com.squareup.anvil.compiler.api.GeneratedFile
import com.squareup.anvil.compiler.api.createGeneratedFile
import com.squareup.anvil.compiler.internal.classesAndInnerClasses
import com.squareup.anvil.compiler.internal.hasAnnotation
import com.squareup.anvil.compiler.internal.safePackageString
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

@AutoService(CodeGenerator::class)
class TestCodeGenerator : CodeGenerator {
  override fun isApplicable(context: AnvilContext): Boolean = true

  override fun generateCode(
    codeGenDir: File,
    module: ModuleDescriptor,
    projectFiles: Collection<KtFile>
  ): Collection<GeneratedFile> {
    return projectFiles.asSequence()
      .flatMap { it.classesAndInnerClasses() }
      .filter { it.hasAnnotation(FqName("com.squareup.anvil.test.Trigger"), module) }
      .flatMap { clazz ->
        val generatedPackage = "generated.test" + clazz.containingKtFile.packageFqName
          .safePackageString(dotPrefix = true, dotSuffix = false)

        @Language("kotlin")
        val generatedClass = """
          package $generatedPackage
          
          class GeneratedClass 
        """.trimIndent()

        @Language("kotlin")
        val contributedInterface = """
          package $generatedPackage
          
          import com.squareup.anvil.annotations.ContributesTo

          @ContributesTo(Unit::class)
          interface ContributedInterface 
        """.trimIndent()

        @Language("kotlin")
        val contributedModule = """
          package $generatedPackage
          
          import com.squareup.anvil.annotations.ContributesTo
          import dagger.Module 

          @ContributesTo(Unit::class)
          @Module
          interface ContributedModule 
        """.trimIndent()

        sequenceOf(
          createGeneratedFile(
            codeGenDir = codeGenDir,
            packageName = generatedPackage,
            fileName = "GeneratedClass",
            content = generatedClass
          ),
          createGeneratedFile(
            codeGenDir = codeGenDir,
            packageName = generatedPackage,
            fileName = "ContributedInterface",
            content = contributedInterface
          ),
          createGeneratedFile(
            codeGenDir = codeGenDir,
            packageName = generatedPackage,
            fileName = "ContributedModule",
            content = contributedModule
          ),
        )
      }
      .toList()
  }
}
