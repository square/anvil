package com.squareup.anvil.compiler.codegen

import com.google.auto.service.AutoService
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.anvil.compiler.codegen.ksp.AnvilSymbolProcessor
import com.squareup.anvil.compiler.codegen.ksp.AnvilSymbolProcessorProvider
import com.squareup.anvil.compiler.daggerComponentFqName
import com.squareup.anvil.compiler.daggerModuleFqName
import com.squareup.anvil.compiler.daggerSubcomponentFqName
import com.squareup.anvil.compiler.internal.ksp.KspAnvilException
import com.squareup.anvil.compiler.internal.ksp.checkNoDuplicateScope
import com.squareup.anvil.compiler.internal.ksp.declaringClass
import com.squareup.anvil.compiler.internal.ksp.fqName
import com.squareup.anvil.compiler.internal.ksp.getClassesWithAnnotations
import com.squareup.anvil.compiler.internal.ksp.getKSAnnotationsByType
import com.squareup.anvil.compiler.internal.ksp.isAnnotationPresent
import com.squareup.anvil.compiler.internal.ksp.mergeAnnotations
import com.squareup.anvil.compiler.internal.ksp.resolveKSClassDeclaration
import com.squareup.anvil.compiler.mergeComponentClassName
import com.squareup.anvil.compiler.mergeComponentFqName
import com.squareup.anvil.compiler.mergeInterfacesFqName
import com.squareup.anvil.compiler.mergeModulesClassName
import com.squareup.anvil.compiler.mergeModulesFqName
import com.squareup.anvil.compiler.mergeSubcomponentClassName
import com.squareup.anvil.compiler.mergeSubcomponentFqName
import com.squareup.kotlinpoet.ksp.toClassName
import dagger.Component
import dagger.Subcomponent
import org.jetbrains.kotlin.name.FqName

internal class KspMergeAnnotationsCheckSymbolProcessor(
  override val env: SymbolProcessorEnvironment,
) : AnvilSymbolProcessor() {

  @AutoService(SymbolProcessorProvider::class)
  class Provider : AnvilSymbolProcessorProvider(
    applicabilityChecker = { context -> !context.disableComponentMerging },
    delegate = ::KspMergeAnnotationsCheckSymbolProcessor,
  )

  override fun processChecked(resolver: Resolver): List<KSAnnotated> {
    resolver.getClassesWithAnnotations(
      mergeComponentFqName,
      mergeSubcomponentFqName,
      mergeModulesFqName,
      mergeInterfacesFqName,
    )
      .forEach(::validate)

    return emptyList()
  }

  companion object {
    fun validate(
      clazz: KSClassDeclaration,
      mergeAnnotations: List<KSAnnotation> = clazz.mergeAnnotations(),
    ) {
      val mergeAnnotation = mergeAnnotations.checkSingleAnnotation()
      mergeAnnotations.checkNoDuplicateScope(
        annotatedType = clazz,
        isContributeAnnotation = false,
      )

      // Note that we only allow a single type of `@Merge*` annotation through the check above.
      // The same class can't merge a component and subcomponent at the same time. Therefore,
      // all annotations must have the same FqName and we can use the first annotation to check
      // for the Dagger annotation.
      mergeAnnotations
        .firstOrNull {
          it.annotationType.resolve().declaration.qualifiedName?.asString() != mergeInterfacesFqName.asString()
        }
        ?.checkNotAnnotatedWithDaggerAnnotation()

      // Validate there is no dagger creator annotation on any nested classes
      val mergeAnnotationFqName = mergeAnnotation.fqName
      if (mergeAnnotationFqName == mergeComponentFqName || mergeAnnotationFqName == mergeSubcomponentFqName) {
        clazz.declarations
          .filterIsInstance<KSClassDeclaration>()
          .forEach {
            it.checkNotAnnotatedWithDaggerCreatorAnnotation(mergeAnnotation)
          }
      }
    }

    private fun List<KSAnnotation>.checkSingleAnnotation(): KSAnnotation {
      val distinctAnnotations = distinctBy { it.annotationType.resolve().declaration.qualifiedName }
      if (distinctAnnotations.size > 1) {
        throw KspAnvilException(
          node = this[0].declaringClass,
          message = "It's only allowed to have one single type of @Merge* annotation, " +
            "however multiple instances of the same annotation are allowed. You mix " +
            distinctAnnotations.joinToString(prefix = "[", postfix = "]") {
              it.shortName.asString()
            } +
            " and this is forbidden.",
        )
      }
      return distinctAnnotations.single()
    }

    private fun KSAnnotation.checkNotAnnotatedWithDaggerAnnotation() {
      if (declaringClass.isAnnotationPresent(daggerAnnotationFqName.asString())) {
        throw KspAnvilException(
          node = declaringClass,
          message = "When using @${shortName.asString()} it's not allowed to " +
            "annotate the same class with @${daggerAnnotationFqName.shortName().asString()}. " +
            "The Dagger annotation will be generated.",
        )
      }
    }

    private fun KSAnnotated.checkNotAnnotatedWithDaggerCreatorAnnotation(
      mergeAnnotation: KSAnnotation,
    ) {
      val isAnnotatedWithDaggerCreator: KSAnnotation? = getKSAnnotationsByType(Component.Factory::class).singleOrNull()
        ?: getKSAnnotationsByType(Component.Builder::class).singleOrNull()
        ?: getKSAnnotationsByType(Subcomponent.Factory::class).singleOrNull()
        ?: getKSAnnotationsByType(Subcomponent.Builder::class).singleOrNull()
      if (isAnnotatedWithDaggerCreator != null) {
        val daggerCreatorFqName = isAnnotatedWithDaggerCreator.fqName
        val creatorType = daggerCreatorFqName.shortName().asString()
        throw KspAnvilException(
          node = isAnnotatedWithDaggerCreator,
          message = "When using @${mergeAnnotation.shortName.asString()}, you must use " +
            "@${mergeAnnotation.shortName.asString()}.$creatorType instead of Dagger's own " +
            "'${daggerCreatorFqName.asString()}' annotation. The Dagger annotation will be " +
            "generated in the final merged component.",
        )
      }
    }

    private val KSAnnotation.daggerAnnotationFqName: FqName
      get() = when (annotationType.resolve().resolveKSClassDeclaration()?.toClassName()) {
        mergeComponentClassName -> daggerComponentFqName
        mergeSubcomponentClassName -> daggerSubcomponentFqName
        mergeModulesClassName -> daggerModuleFqName
        else -> throw NotImplementedError("Don't know how to handle $this.")
      }
  }
}
