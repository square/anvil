package com.squareup.anvil.compiler.codegen.dagger

import com.google.auto.service.AutoService
import com.squareup.anvil.compiler.api.AnvilApplicabilityChecker
import com.squareup.anvil.compiler.api.AnvilContext
import com.squareup.anvil.compiler.api.CodeGenerator
import com.squareup.anvil.compiler.codegen.CheckOnlyCodeGenerator
import com.squareup.anvil.compiler.daggerComponentFqName
import com.squareup.anvil.compiler.internal.reference.AnvilCompilationExceptionClassReference
import com.squareup.anvil.compiler.internal.reference.classAndInnerClassReferences
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

internal object ComponentDetectorCheck : AnvilApplicabilityChecker {
  private const val MESSAGE =
    "Anvil cannot generate the code for Dagger components or subcomponents. In " +
      "these cases the Dagger annotation processor is required. Enabling the Dagger " +
      "annotation processor and turning on Anvil to generate Dagger factories is " +
      "redundant. Set 'generateDaggerFactories' to false."
  private val ANNOTATIONS_TO_CHECK = setOf(daggerComponentFqName)
  override fun isApplicable(context: AnvilContext) = context.generateFactories

  @AutoService(CodeGenerator::class)
  internal class EmbeddedGenerator : CheckOnlyCodeGenerator() {

    override fun isApplicable(context: AnvilContext) =
      ComponentDetectorCheck.isApplicable(context)

    override fun checkCode(
      codeGenDir: File,
      module: ModuleDescriptor,
      projectFiles: Collection<KtFile>,
    ) {
      val clazz =
        projectFiles.classAndInnerClassReferences(module).firstOrNull { clazz ->
          ANNOTATIONS_TO_CHECK.any { clazz.isAnnotatedWith(it) }
        }

      if (clazz != null) {
        throw AnvilCompilationExceptionClassReference(
          message = MESSAGE,
          classReference = clazz,
        )
      }
    }
  }
}
