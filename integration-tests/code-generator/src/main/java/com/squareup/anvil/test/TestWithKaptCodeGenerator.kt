package com.squareup.anvil.test

import com.google.auto.service.AutoService
import com.squareup.anvil.compiler.api.AnvilContext
import com.squareup.anvil.compiler.api.CodeGenerator
import com.squareup.anvil.compiler.api.GeneratedFile
import com.squareup.anvil.compiler.api.createGeneratedFile
import com.squareup.anvil.compiler.internal.reference.classAndInnerClassReferences
import com.squareup.anvil.compiler.internal.reference.isAnnotatedWith
import com.squareup.anvil.compiler.internal.safePackageString
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

/**
 * This generator creates code which needs Dagger kapt to run on its source set,
 * such as interfaces which are annotated with @MergeComponent or @MergeSubcomponent.
 */
@Suppress("unused")
@AutoService(CodeGenerator::class)
class TestWithKaptCodeGenerator : CodeGenerator {
  override fun isApplicable(context: AnvilContext): Boolean = true

  override fun generateCode(
    codeGenDir: File,
    module: ModuleDescriptor,
    projectFiles: Collection<KtFile>
  ): Collection<GeneratedFile> {
    return projectFiles
      .classAndInnerClassReferences(module)
      .filter { it.isAnnotatedWith(FqName("com.squareup.anvil.test.TriggerWithKapt")) }
      .flatMap { clazz ->
        val generatedPackage = "generated.test" + clazz.packageFqName
          .safePackageString(dotPrefix = true, dotSuffix = false)

        @Language("kotlin")
        val mergedComponent = """
          package $generatedPackage
          
          import com.squareup.anvil.annotations.MergeComponent

          @MergeComponent(Unit::class)
          interface MergedComponent 
        """.trimIndent()

        sequenceOf(
          createGeneratedFile(
            codeGenDir = codeGenDir,
            packageName = generatedPackage,
            fileName = "mergedComponent",
            content = mergedComponent
          ),
        )
      }
      .toList()
  }
}
