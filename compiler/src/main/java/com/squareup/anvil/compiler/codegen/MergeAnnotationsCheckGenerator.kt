package com.squareup.anvil.compiler.codegen

import com.google.auto.service.AutoService
import com.squareup.anvil.compiler.api.AnvilContext
import com.squareup.anvil.compiler.api.CodeGenerator
import com.squareup.anvil.compiler.internal.reference.AnnotationReference
import com.squareup.anvil.compiler.internal.reference.AnvilCompilationExceptionClassReference
import com.squareup.anvil.compiler.internal.reference.classAndInnerClassReferences
import com.squareup.anvil.compiler.mergeComponentFqName
import com.squareup.anvil.compiler.mergeInterfacesFqName
import com.squareup.anvil.compiler.mergeModulesFqName
import com.squareup.anvil.compiler.mergeSubcomponentFqName
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

@AutoService(CodeGenerator::class)
internal class MergeAnnotationsCheckGenerator : PrivateCodeGenerator() {
  override fun isApplicable(context: AnvilContext): Boolean = !context.disableComponentMerging

  override fun generateCodePrivate(
    codeGenDir: File,
    module: ModuleDescriptor,
    projectFiles: Collection<KtFile>
  ) {
    projectFiles
      .classAndInnerClassReferences(module)
      .forEach { clazz ->
        val annotations = clazz.annotations
          .findAll(
            mergeComponentFqName,
            mergeSubcomponentFqName,
            mergeModulesFqName,
            mergeInterfacesFqName
          )
          .ifEmpty { return@forEach }

        annotations.checkSingleAnnotation()
        annotations.checkNoDuplicateScope(contributeAnnotation = false)

        // Note that we only allow a single type of `@Merge*` annotation through the check above.
        // The same class can't merge a component and subcomponent at the same time. Therefore,
        // all annotations must have the same FqName and we can use the first annotation to check
        // for the Dagger annotation.
        annotations
          .firstOrNull { it.fqName != mergeInterfacesFqName }
          ?.checkNotAnnotatedWithDaggerAnnotation()
      }
  }

  private fun List<AnnotationReference>.checkSingleAnnotation() {
    val distinctAnnotations = distinctBy { it.fqName }
    if (distinctAnnotations.size > 1) {
      throw AnvilCompilationExceptionClassReference(
        classReference = this[0].declaringClass(),
        message = "It's only allowed to have one single type of @Merge* annotation, " +
          "however multiple instances of the same annotation are allowed. You mix " +
          distinctAnnotations.joinToString(prefix = "[", postfix = "]") {
            it.fqName.shortName().asString()
          } +
          " and this is forbidden."
      )
    }
  }

  private fun AnnotationReference.checkNotAnnotatedWithDaggerAnnotation() {
    if (declaringClass().isAnnotatedWith(daggerAnnotationFqName)) {
      throw AnvilCompilationExceptionClassReference(
        classReference = declaringClass(),
        message = "When using @${fqName.shortName()} it's not allowed to " +
          "annotate the same class with @${daggerAnnotationFqName.shortName()}. " +
          "The Dagger annotation will be generated."
      )
    }
  }
}
