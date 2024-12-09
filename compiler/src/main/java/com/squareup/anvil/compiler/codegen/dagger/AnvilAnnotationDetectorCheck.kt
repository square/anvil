package com.squareup.anvil.compiler.codegen.dagger

import com.google.auto.service.AutoService
import com.squareup.anvil.compiler.api.AnvilApplicabilityChecker
import com.squareup.anvil.compiler.api.AnvilContext
import com.squareup.anvil.compiler.api.CodeGenerator
import com.squareup.anvil.compiler.codegen.CheckOnlyCodeGenerator
import com.squareup.anvil.compiler.contributesBindingFqName
import com.squareup.anvil.compiler.contributesSubcomponentFqName
import com.squareup.anvil.compiler.contributesToFqName
import com.squareup.anvil.compiler.internal.reference.AnvilCompilationExceptionClassReference
import com.squareup.anvil.compiler.internal.reference.classAndInnerClassReferences
import com.squareup.anvil.compiler.mergeComponentFqName
import com.squareup.anvil.compiler.mergeInterfacesFqName
import com.squareup.anvil.compiler.mergeModulesFqName
import com.squareup.anvil.compiler.mergeSubcomponentFqName
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

internal object AnvilAnnotationDetectorCheck : AnvilApplicabilityChecker {

  private const val MESSAGE = "This Gradle module is configured to ONLY " +
    "generate Dagger factories with the `generateDaggerFactoriesOnly`" +
    " flag. However, this module contains code that " +
    "uses other Anvil annotations. That's not supported."
  private val ANNOTATIONS_TO_CHECK = setOf(
    mergeComponentFqName,
    mergeSubcomponentFqName,
    mergeInterfacesFqName,
    mergeModulesFqName,
    contributesToFqName,
    contributesSubcomponentFqName,
    contributesBindingFqName,
  )

  override fun isApplicable(context: AnvilContext) = context.generateFactoriesOnly &&
    !context.disableComponentMerging

  @AutoService(CodeGenerator::class)
  internal class EmbeddedGenerator : CheckOnlyCodeGenerator() {

    override fun isApplicable(context: AnvilContext) =
      AnvilAnnotationDetectorCheck.isApplicable(context)

    override fun checkCode(
      codeGenDir: File,
      module: ModuleDescriptor,
      projectFiles: Collection<KtFile>,
    ) {
      val clazz = projectFiles
        .classAndInnerClassReferences(module)
        .firstOrNull { clazz ->
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
