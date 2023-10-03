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
import com.squareup.anvil.compiler.daggerComponentFqName
import com.squareup.anvil.compiler.internal.reference.AnvilCompilationExceptionClassReference
import com.squareup.anvil.compiler.internal.reference.classAndInnerClassReferences
import java.io.File
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtFile

internal object ComponentDetectorCheck : AnvilApplicabilityChecker {
  private const val MESSAGE =
      "Anvil cannot generate the code for Dagger components or subcomponents. In " +
          "these cases the Dagger annotation processor is required. Enabling the Dagger " +
          "annotation processor and turning on Anvil to generate Dagger factories is " +
          "redundant. Set 'generateDaggerFactories' to false."
  private val ANNOTATIONS_TO_CHECK = setOf(daggerComponentFqName)
  override fun isApplicable(context: AnvilContext) = context.generateFactories

  internal class KspGenerator(
      override val env: SymbolProcessorEnvironment,
  ) : AnvilSymbolProcessor() {
    @AutoService(SymbolProcessorProvider::class)
    class Provider : AnvilSymbolProcessorProvider(ComponentDetectorCheck, ::KspGenerator)

    override fun processChecked(resolver: Resolver): List<KSAnnotated> {
      val clazz =
          ANNOTATIONS_TO_CHECK.flatMap { resolver.getSymbolsWithAnnotation(it.asString()) }
              .firstOrNull()
              ?: return emptyList()

      throw KspAnvilException(message = MESSAGE, node = clazz)
    }
  }

  @AutoService(CodeGenerator::class)
  internal class EmbeddedGenerator : PrivateCodeGenerator() {

    override fun isApplicable(context: AnvilContext) =
      ComponentDetectorCheck.isApplicable(context)

    override fun generateCodePrivate(
        codeGenDir: File,
        module: ModuleDescriptor,
        projectFiles: Collection<KtFile>
    ) {
      val clazz =
          projectFiles.classAndInnerClassReferences(module).firstOrNull {
            clazz -> ANNOTATIONS_TO_CHECK.any { clazz.isAnnotatedWith(it) }
          }

      if (clazz != null) {
        throw AnvilCompilationExceptionClassReference(
            message = MESSAGE,
            classReference = clazz
        )
      }
    }
  }
}
