package com.squareup.anvil.compiler

import com.google.auto.service.AutoService
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.anvil.compiler.codegen.ksp.AnvilSymbolProcessor
import com.squareup.anvil.compiler.codegen.ksp.AnvilSymbolProcessorProvider
import com.squareup.anvil.compiler.codegen.ksp.KspAnvilException
import com.squareup.anvil.compiler.codegen.ksp.findAll
import com.squareup.anvil.compiler.codegen.ksp.getSymbolsWithAnnotations
import com.squareup.anvil.compiler.codegen.ksp.isInterface
import com.squareup.kotlinpoet.AnnotationSpec

/**
 * A [com.google.devtools.ksp.processing.SymbolProcessor] that performs the two types of merging
 * Anvil supports.
 *
 * 1. **Module merging**: This step sources from `@MergeComponent`, `@MergeSubcomponent`, and
 * `@MergeModules` to merge all contributed modules on the classpath to the generated element.
 *
 * 2. **Interface merging**: This step finds all contributed component interfaces and adds them
 * as supertypes to generated Dagger components from interfaces annotated with `@MergeComponent`
 * or `@MergeSubcomponent`. This also supports arbitrary interface merging on interfaces annotated
 * with `@MergeInterfaces`.
 */
internal class KspContributionMerger(override val env: SymbolProcessorEnvironment) :
  AnvilSymbolProcessor() {

  @AutoService(SymbolProcessorProvider::class)
  class Provider : AnvilSymbolProcessorProvider(
    { context -> !context.disableComponentMerging && !context.generateFactories },
    ::KspContributionMerger,
  )

  override fun processChecked(
    resolver: Resolver
  ): List<KSAnnotated> {
    // TODO how do we ensure this runs last?
    //  - run all other processors first?
    //  - check no *Contributes symbols are left?

    val deferred = resolver.getSymbolsWithAnnotations(
      mergeComponentFqName,
      mergeSubcomponentFqName,
      mergeModulesFqName,
      mergeInterfacesFqName,
    ).validate { deferred -> return deferred }
      .mapNotNull { annotated ->
        processClass(resolver, annotated)
      }

    return deferred
  }

  /**
   * Returns non-null if the given [annotated] could not be processed.
   */
  private fun processClass(
    resolver: Resolver,
    mergeAnnotatedClass: KSClassDeclaration
  ): KSAnnotated? {
    val mergeComponentAnnotations = mergeAnnotatedClass
      .findAll(mergeComponentFqName, mergeSubcomponentFqName)

    val mergeModulesAnnotations = mergeAnnotatedClass
      .findAll(mergeModulesFqName)

    val moduleMergerAnnotations = mergeComponentAnnotations + mergeModulesAnnotations

    val daggerAnnotation = if (moduleMergerAnnotations.isNotEmpty()) {
      generateDaggerAnnotation(
        annotations = moduleMergerAnnotations,
        resolver = resolver,
        declaration = mergeAnnotatedClass,
      )
    } else {
      null
    }

    val mergeInterfacesAnnotations = mergeAnnotatedClass
      .findAll(mergeInterfacesFqName)

    val interfaceMergerAnnotations = mergeComponentAnnotations + mergeInterfacesAnnotations

    val contributedInterfaces = if (interfaceMergerAnnotations.isNotEmpty()) {
      if (!mergeAnnotatedClass.isInterface()) {
        throw KspAnvilException(
          node = mergeAnnotatedClass,
          message = "Dagger components (or classes annotated with @MergeInterfaces)" +
            " must be interfaces.",
        )
      }

      contributedInterfaces(
        mergeAnnotations = interfaceMergerAnnotations,
        resolver = resolver,
        mergeAnnotatedClass = mergeAnnotatedClass,
      )
    } else {
      null
    }

    if (contributedInterfaces != null || daggerAnnotation != null) {
      generateMergedComponent(
        mergeAnnotatedClass = mergeAnnotatedClass,
        daggerAnnotation = daggerAnnotation,
        contributedInterfaces = contributedInterfaces,
        resolver = resolver,
      )
    }
    return null
  }

  private fun generateDaggerAnnotation(
    annotations: List<KSAnnotation>,
    resolver: Resolver,
    declaration: KSClassDeclaration,
  ): AnnotationSpec {
    TODO()
  }

  private fun contributedInterfaces(
    mergeAnnotations: List<KSAnnotation>,
    resolver: Resolver,
    mergeAnnotatedClass: KSClassDeclaration,
  ): List<KSClassDeclaration> {
    TODO()
  }

  private fun generateMergedComponent(
    mergeAnnotatedClass: KSClassDeclaration,
    daggerAnnotation: AnnotationSpec?,
    contributedInterfaces: List<KSClassDeclaration>?,
    resolver: Resolver,
  ) {
    TODO()
  }

  private inline fun Sequence<KSAnnotated>.validate(
    escape: (List<KSAnnotated>) -> Nothing
  ): List<KSClassDeclaration> {
    val (valid, deferred) = filterIsInstance<KSClassDeclaration>().partition { annotated ->
      // TODO check error types in annotations props
      !annotated.superTypes.any { it.resolve().isError }
    }
    return if (deferred.isNotEmpty()) {
      escape(deferred)
    } else {
      valid
    }
  }
}
