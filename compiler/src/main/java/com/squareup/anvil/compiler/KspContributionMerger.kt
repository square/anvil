package com.squareup.anvil.compiler

import com.google.auto.service.AutoService
import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.processing.impl.KSNameImpl
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSName
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Visibility
import com.squareup.anvil.compiler.codegen.generatedAnvilSubcomponentClassId
import com.squareup.anvil.compiler.codegen.ksp.AnvilSymbolProcessor
import com.squareup.anvil.compiler.codegen.ksp.AnvilSymbolProcessorProvider
import com.squareup.anvil.compiler.codegen.ksp.KspAnvilException
import com.squareup.anvil.compiler.codegen.ksp.atLeastOneAnnotation
import com.squareup.anvil.compiler.codegen.ksp.declaringClass
import com.squareup.anvil.compiler.codegen.ksp.exclude
import com.squareup.anvil.compiler.codegen.ksp.findAll
import com.squareup.anvil.compiler.codegen.ksp.getSymbolsWithAnnotations
import com.squareup.anvil.compiler.codegen.ksp.isInterface
import com.squareup.anvil.compiler.codegen.ksp.parentScope
import com.squareup.anvil.compiler.codegen.ksp.replaces
import com.squareup.anvil.compiler.codegen.ksp.resolveKSClassDeclaration
import com.squareup.anvil.compiler.codegen.ksp.scope
import com.squareup.anvil.compiler.codegen.ksp.superTypesExcludingAny
import com.squareup.anvil.compiler.internal.reference.asClassId
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ksp.toClassName
import org.jetbrains.kotlin.name.Name

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

  private val classScanner = ClassScannerKsp()

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
      .findAll(mergeComponentFqName.asString(), mergeSubcomponentFqName.asString())

    val mergeModulesAnnotations = mergeAnnotatedClass
      .findAll(mergeModulesFqName.asString())

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
      .findAll(mergeInterfacesFqName.asString())

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
  ): List<KSType> {
    val scopes = mergeAnnotations.map { it.scope() }
    val contributesAnnotations = mergeAnnotations
      .flatMap { annotation ->
        classScanner
          .findContributedClasses(
            resolver = resolver,
            annotation = contributesToFqName,
            scope = annotation.scope(),
          )
      }
      .asSequence()
      .filter { clazz ->
        clazz.isInterface() && clazz.findAll(daggerModuleFqName.asString()).singleOrNull() == null
      }
      .flatMap { clazz ->
        clazz
          .findAll(contributesToFqName.asString())
          .filter { it.scope() in scopes }
      }
      .onEach { contributeAnnotation ->
        val contributedClass = contributeAnnotation.declaringClass
        if (contributedClass.getVisibility() != Visibility.PUBLIC) {
          throw KspAnvilException(
            node = contributedClass,
            message = "${contributedClass.qualifiedName?.asString()} is contributed to the Dagger graph, but the " +
              "interface is not public. Only public interfaces are supported.",
          )
        }
      }
      // Convert the sequence to a list to avoid iterating it twice. We use the result twice
      // for replaced classes and the final result.
      .toList()

    val replacedClasses = contributesAnnotations
      .flatMap { contributeAnnotation ->
        val contributedClass = contributeAnnotation.declaringClass
        contributedClass
          .atLeastOneAnnotation(
            contributeAnnotation.annotationType.resolve()
              .resolveKSClassDeclaration()!!.qualifiedName!!.asString(),
          )
          .asSequence()
          .flatMap { it.replaces() }
          .onEach { classToReplace ->
            // Verify the other class is an interface. It doesn't make sense for a contributed
            // interface to replace a class that is not an interface.
            if (!classToReplace.isInterface()) {
              throw KspAnvilException(
                node = contributedClass,
                message = "${contributedClass.qualifiedName?.asString()} wants to replace " +
                  "${classToReplace.qualifiedName?.asString()}, but the class being " +
                  "replaced is not an interface.",
              )
            }

            val contributesToOurScope = classToReplace
              .findAll(
                contributesToFqName.asString(),
                contributesBindingFqName.asString(),
                contributesMultibindingFqName.asString(),
              )
              .map { it.scope() }
              .any { scope -> scope in scopes }

            if (!contributesToOurScope) {
              throw KspAnvilException(
                node = contributedClass,
                message = "${contributedClass.qualifiedName?.asString()} with scopes " +
                  "${
                    scopes.joinToString(
                      prefix = "[",
                      postfix = "]",
                    ) { it.resolveKSClassDeclaration()?.qualifiedName?.asString()!! }
                  } " +
                  "wants to replace ${classToReplace.qualifiedName?.asString()}, but the replaced class isn't " +
                  "contributed to the same scope.",
              )
            }
          }
      }
      .toSet()

    val excludedClasses = mergeAnnotations
      .asSequence()
      .flatMap { it.exclude() }
      .filter { it.isInterface() }
      .onEach { excludedClass ->
        // Verify that the replaced classes use the same scope.
        val contributesToOurScope = excludedClass
          .findAll(
            contributesToFqName.asString(),
            contributesBindingFqName.asString(),
            contributesMultibindingFqName.asString(),
          )
          .map { it.scope() }
          .plus(
            excludedClass.findAll(contributesSubcomponentFqName.asString())
              .map { it.parentScope() },
          )
          .any { scope -> scope in scopes }

        if (!contributesToOurScope) {
          throw KspAnvilException(
            message = "${mergeAnnotatedClass.qualifiedName?.asString()} with scopes " +
              "${
                scopes.joinToString(
                  prefix = "[",
                  postfix = "]",
                ) { it.resolveKSClassDeclaration()?.qualifiedName?.asString()!! }
              } " +
              "wants to exclude ${excludedClass.qualifiedName?.asString()}, but the excluded class isn't " +
              "contributed to the same scope.",
            node = mergeAnnotatedClass,
          )
        }
      }
      .toList()

    val supertypes = mergeAnnotatedClass.superTypesExcludingAny(resolver)
    if (excludedClasses.isNotEmpty()) {
      val intersect = supertypes
        .mapNotNull { it.resolve().resolveKSClassDeclaration() }
        .flatMap {
          it.getAllSuperTypes()
        }
        .mapNotNull { it.resolveKSClassDeclaration() }
        .toSet()
        .intersect(excludedClasses.toSet())

      if (intersect.isNotEmpty()) {
        throw KspAnvilException(
          node = mergeAnnotatedClass,
          message = "${mergeAnnotatedClass.qualifiedName?.asString()} excludes types that it implements or " +
            "extends. These types cannot be excluded. Look at all the super types to find these " +
            "classes: ${intersect.joinToString { it.qualifiedName?.asString()!! }}.",
        )
      }
    }

    val supertypesToAdd = contributesAnnotations
      .asSequence()
      .map { it.declaringClass }
      .filter { clazz ->
        clazz !in replacedClasses && clazz !in excludedClasses
      }
      .plus(
        findContributedSubcomponentParentInterfaces(
          clazz = mergeAnnotatedClass,
          scopes = scopes,
          resolver = resolver,
        ),
      )
      // Avoids an error for repeated interfaces.
      .distinct()
      .map { it.asType(emptyList()) }
      .toList()

    return supertypesToAdd
  }

  private fun generateMergedComponent(
    mergeAnnotatedClass: KSClassDeclaration,
    daggerAnnotation: AnnotationSpec?,
    contributedInterfaces: List<KSType>?,
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

  private fun findContributedSubcomponentParentInterfaces(
    clazz: KSClassDeclaration,
    scopes: Collection<KSType>,
    resolver: Resolver,
  ): Sequence<KSClassDeclaration> {
    return classScanner
      .findContributedClasses(
        resolver = resolver,
        annotation = contributesSubcomponentFqName,
        scope = null,
      )
      .filter {
        it.atLeastOneAnnotation(contributesSubcomponentFqName.asString()).single()
          .parentScope().asType(emptyList()) in scopes
      }
      .mapNotNull { contributedSubcomponent ->
        contributedSubcomponent.toClassName().asClassId()
          .generatedAnvilSubcomponentClassId(clazz.toClassName().asClassId())
          .createNestedClassId(Name.identifier(PARENT_COMPONENT))
          .let { classId ->
            resolver.getClassDeclarationByName(
              resolver.getKSNameFromString(
                classId.asSingleFqName()
                  .toString(),
              ),
            )
          }
      }
  }
}
