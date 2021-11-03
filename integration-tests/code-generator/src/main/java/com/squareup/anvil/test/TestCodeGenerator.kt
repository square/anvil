package com.squareup.anvil.test

import com.google.auto.service.AutoService
import com.squareup.anvil.compiler.api.AnvilContext
import com.squareup.anvil.compiler.api.CodeGenerator
import com.squareup.anvil.compiler.api.GeneratedFile
import com.squareup.anvil.compiler.api.createGeneratedFile
import com.squareup.anvil.compiler.internal.classesAndInnerClass
import com.squareup.anvil.compiler.internal.hasAnnotation
import com.squareup.anvil.compiler.internal.safePackageString
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

/**
 * This generator creates code which can go in a source set which uses Anvil's factory generation.
 *
 * This is necessary because files with @Inject or @AssistedInject will generate duplicate classes
 * if they trigger both Anvil and Dagger generation.
 */
@Suppress("unused")
@AutoService(CodeGenerator::class)
class TestCodeGenerator : CodeGenerator {
  override fun isApplicable(context: AnvilContext): Boolean = true

  override fun generateCode(
    codeGenDir: File,
    module: ModuleDescriptor,
    projectFiles: Collection<KtFile>
  ): Collection<GeneratedFile> {
    return projectFiles
      .classesAndInnerClass(module)
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

        // This snippet is helpful for testing #310, which is still failing.
        @Language("kotlin")
        val contributedBinding = """
          /*
          package $generatedPackage
          
          import com.squareup.anvil.annotations.ContributesBinding
          import javax.inject.Inject

          @ContributesBinding(Unit::class)
          abstract class ContributedBinding @Inject constructor() : CharSequence
           */
        """.trimIndent()

        @Language("kotlin")
        val injectClass = """
          package $generatedPackage
          
          import javax.inject.Inject
    
          class InjectClass @Inject constructor() 
        """.trimIndent()

        // This snippet is helpful for testing #326.
        @Language("kotlin")
        val assistedInject = """
          package $generatedPackage
          
          import dagger.assisted.Assisted
          import dagger.assisted.AssistedFactory
          import dagger.assisted.AssistedInject

          data class AssistedService @AssistedInject constructor(
            @Assisted val string: String
          )

          interface ParentAssistedFactory : Function1<String, AssistedService>
    
          @AssistedFactory
          interface SampleAssistedFactory : ParentAssistedFactory
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
          createGeneratedFile(
            codeGenDir = codeGenDir,
            packageName = generatedPackage,
            fileName = "ContributedBinding",
            content = contributedBinding
          ),
          createGeneratedFile(
            codeGenDir = codeGenDir,
            packageName = generatedPackage,
            fileName = "InjectClass",
            content = injectClass
          ),
          createGeneratedFile(
            codeGenDir = codeGenDir,
            packageName = generatedPackage,
            fileName = "AssistedInject",
            content = assistedInject
          ),
        )
      }
      .toList()
  }
}
