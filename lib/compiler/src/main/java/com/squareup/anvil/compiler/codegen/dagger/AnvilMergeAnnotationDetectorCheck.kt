package com.squareup.anvil.compiler.codegen.dagger

import com.google.auto.service.AutoService
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.squareup.anvil.compiler.api.AnvilApplicabilityChecker
import com.squareup.anvil.compiler.api.AnvilContext
import com.squareup.anvil.compiler.api.CodeGenerator
import com.squareup.anvil.compiler.codegen.PrivateCodeGenerator
import com.squareup.anvil.compiler.codegen.ksp.AnvilSymbolProcessor
import com.squareup.anvil.compiler.codegen.ksp.AnvilSymbolProcessorProvider
import com.squareup.anvil.compiler.codegen.ksp.KspAnvilException
import com.squareup.anvil.compiler.internal.reference.AnvilCompilationExceptionClassReference
import com.squareup.anvil.compiler.internal.reference.classAndInnerClassReferences
import com.squareup.anvil.compiler.mergeComponentFqName
import com.squareup.anvil.compiler.mergeInterfacesFqName
import com.squareup.anvil.compiler.mergeModulesFqName
import com.squareup.anvil.compiler.mergeSubcomponentFqName
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

internal object AnvilMergeAnnotationDetectorCheck : AnvilApplicabilityChecker {
  private const val MESSAGE = "This Gradle module is configured to ONLY generate code with " +
    "the `disableComponentMerging` flag. However, this module contains code that " +
    "uses Anvil @Merge* annotations. That's not supported."
  private val ANNOTATIONS_TO_CHECK = setOf(
    mergeComponentFqName,
    mergeSubcomponentFqName,
    mergeInterfacesFqName,
    mergeModulesFqName,
  )

  override fun isApplicable(context: AnvilContext) = context.disableComponentMerging

  internal class KspGenerator(
    override val env: SymbolProcessorEnvironment,
  ) : AnvilSymbolProcessor() {
    @AutoService(SymbolProcessorProvider::class)
    class Provider : AnvilSymbolProcessorProvider(AnvilMergeAnnotationDetectorCheck, ::KspGenerator)

    override fun processChecked(resolver: Resolver): List<KSAnnotated> {
      val clazz = ANNOTATIONS_TO_CHECK
        .asSequence()
        .flatMap {
          resolver.getSymbolsWithAnnotation(it.asString())
        }
        .firstOrNull()
        ?: return emptyList()

      throw KspAnvilException(
        message = MESSAGE,
        node = clazz,
      )
    }
  }

  @AutoService(CodeGenerator::class)
  internal class EmbeddedGenerator : PrivateCodeGenerator() {

    override fun isApplicable(context: AnvilContext) =
      AnvilMergeAnnotationDetectorCheck.isApplicable(context)

    override fun generateCodePrivate(
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
