package com.squareup.anvil.compiler.ksp

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.isAnnotationPresent
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.anvil.annotations.ContributesSubcomponent
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.anvil.annotations.MergeComponent
import com.squareup.anvil.annotations.MergeSubcomponent
import com.squareup.anvil.annotations.compat.MergeInterfaces
import com.squareup.anvil.compiler.PARENT_COMPONENT
import com.squareup.anvil.compiler.codegen.generatedAnvilSubcomponent
import com.squareup.anvil.compiler.codegen.ksp.KspAnvilException
import com.squareup.anvil.compiler.codegen.ksp.isInterface
import com.squareup.anvil.compiler.codegen.ksp.scope
import com.squareup.anvil.compiler.contributesSubcomponentFqName
import com.squareup.anvil.compiler.contributesToFqName
import dagger.Module
import org.jetbrains.kotlin.name.Name

/**
 * Finds all contributed component interfaces and adds them as super types to Dagger components
 * annotated with `@MergeComponent` or `@MergeSubcomponent`.
 */
internal class InterfaceMergerKSP(
  private val classScanner: ClassScannerKSP,
) {
  fun computeSyntheticSupertypes(
    mergeAnnotatedClass: KSClassDeclaration,
    resolver: Resolver,
    scopes: Set<KSClassDeclaration>,
  ): List<KSType> {
    if (mergeAnnotatedClass.shouldIgnore()) return emptyList()

    val mergeAnnotations = mergeAnnotatedClass
      .findAllKSAnnotations(MergeComponent::class, MergeSubcomponent::class, MergeInterfaces::class)
      .toList()
      .ifEmpty { return emptyList() }

    val supertypes = mergeAnnotatedClass.getAllSuperTypes()
      .toMutableList()

    val result = mergeInterfaces(
      classScanner = classScanner,
      resolver = resolver,
      mergeAnnotatedClass = mergeAnnotatedClass,
      annotations = mergeAnnotations,
      supertypes = supertypes,
      scopes = scopes,
    )

    supertypes += result.contributesAnnotations
      .asSequence()
      .map { it.declaringClass() }
      .filter { clazz ->
        clazz !in result.replacedClasses && clazz !in result.excludedClasses
      }
      .plus(result.contributedSubcomponentInterfaces())
      // Avoids an error for repeated interfaces.
      .distinct()
      .map { it.asType(emptyList()) }
      .toList()

    return supertypes
  }

  data class MergeResult(
    val contributesAnnotations: List<KSAnnotation>,
    val replacedClasses: Set<KSClassDeclaration>,
    val excludedClasses: List<KSClassDeclaration>,
    val contributedSubcomponentInterfaces: () -> Sequence<KSClassDeclaration>,
  )

  companion object {
    @OptIn(KspExperimental::class)
    private fun mergeInterfaces(
      classScanner: ClassScannerKSP,
      resolver: Resolver,
      mergeAnnotatedClass: KSClassDeclaration,
      annotations: List<KSAnnotation>,
      supertypes: List<KSType>,
      scopes: Set<KSClassDeclaration>,
    ): MergeResult {
      if (!mergeAnnotatedClass.isInterface()) {
        throw KspAnvilException(
          node = mergeAnnotatedClass,
          message = "Dagger components must be interfaces.",
        )
      }

      val contributesAnnotations = annotations
        .flatMap { annotation ->
          classScanner
            .findContributedClasses(
              resolver = resolver,
              annotation = contributesToFqName,
              scope = annotation.scope(),
            )
        }
        .filter { clazz ->
          clazz.isInterface() && !clazz.isAnnotationPresent(Module::class)
        }
        .flatMap { clazz ->
          clazz.findAllKSAnnotations(ContributesTo::class)
            .filter { it.scope() in scopes }
        }
        .onEach { contributeAnnotation ->
          val contributedClass = contributeAnnotation.declaringClass()
          if (!contributedClass.isPublic()) {
            throw KspAnvilException(
              node = contributedClass,
              message = "${contributedClass.fqName} is contributed to the Dagger graph, but the " +
                "interface is not public. Only public interfaces are supported.",
            )
          }
        }
        // Convert the sequence to a list to avoid iterating it twice. We use the result twice
        // for replaced classes and the final result.
        .toList()

      val replacedClasses = contributesAnnotations
        .flatMap { contributeAnnotation ->
          val contributedClass = contributeAnnotation.declaringClass()
          contributedClass
            .atLeastOneAnnotation(
              contributeAnnotation.annotationType.resolve().classDeclaration.qualifiedName!!.asString(),
            )
            .flatMap { it.replaces() }
            .map { it.classDeclaration }
            .onEach { classToReplace ->
              // Verify the other class is an interface. It doesn't make sense for a contributed
              // interface to replace a class that is not an interface.
              if (!classToReplace.isInterface()) {
                throw KspAnvilException(
                  node = contributedClass,
                  message = "${contributedClass.fqName} wants to replace " +
                    "${classToReplace.fqName}, but the class being " +
                    "replaced is not an interface.",
                )
              }

              val contributesToOurScope = classToReplace
                .findAllKSAnnotations(
                  ContributesTo::class,
                  ContributesBinding::class,
                  ContributesMultibinding::class,
                )
                .map { it.scope() }
                .any { scope -> scope in scopes }

              if (!contributesToOurScope) {
                val scopesString = scopes.joinToString(prefix = "[", postfix = "]") {
                  it.fqName.asString()
                }
                throw KspAnvilException(
                  node = contributedClass,
                  message = "${contributedClass.fqName} with scopes " +
                    "$scopesString " +
                    "wants to replace ${classToReplace.fqName}, but the replaced class isn't " +
                    "contributed to the same scope.",
                )
              }
            }
        }
        .toSet()

      val excludedClasses = annotations
        .flatMap { it.exclude() }
        .map { it.classDeclaration }
        .filter { it.isInterface() }
        .onEach { excludedClass ->
          // Verify that the replaced classes use the same scope.
          val contributesToOurScope = excludedClass
            .findAllKSAnnotations(
              ContributesTo::class,
              ContributesBinding::class,
              ContributesMultibinding::class,
            )
            .map { it.scope() }
            .plus(
              excludedClass.findAllKSAnnotations(
                ContributesSubcomponent::class,
              ).map { it.parentScope() },
            )
            .any { scope -> scope in scopes }

          if (!contributesToOurScope) {
            throw KspAnvilException(
              message = "${mergeAnnotatedClass.fqName} with scopes " +
                "${scopes.joinToString(prefix = "[", postfix = "]") { it.fqName.asString() }} " +
                "wants to exclude ${excludedClass.fqName}, but the excluded class isn't " +
                "contributed to the same scope.",
              node = mergeAnnotatedClass,
            )
          }
        }

      if (excludedClasses.isNotEmpty()) {
        val intersect = supertypes
          .map { it.classDeclaration }
          .intersect(excludedClasses.toSet())

        if (intersect.isNotEmpty()) {
          throw KspAnvilException(
            node = mergeAnnotatedClass,
            message = "${mergeAnnotatedClass.fqName} excludes types that it implements or " +
              "extends. These types cannot be excluded. Look at all the super" +
              " types to find these classes: " +
              "${intersect.joinToString { it.fqName.asString() }}.",
          )
        }
      }
      return MergeResult(
        contributesAnnotations = contributesAnnotations,
        replacedClasses = replacedClasses,
        excludedClasses = excludedClasses,
        contributedSubcomponentInterfaces = {
          findContributedSubcomponentParentInterfaces(
            classScanner = classScanner,
            clazz = mergeAnnotatedClass,
            scopes = scopes,
            resolver = resolver,
          )
        },
      )
    }

    private fun findContributedSubcomponentParentInterfaces(
      classScanner: ClassScannerKSP,
      clazz: KSClassDeclaration,
      scopes: Collection<KSClassDeclaration>,
      resolver: Resolver,
    ): Sequence<KSClassDeclaration> {
      return classScanner
        .findContributedClasses(
          resolver = resolver,
          annotation = contributesSubcomponentFqName,
          scope = null,
        )
        .filter {
          it.atLeastOneAnnotation(ContributesSubcomponent::class).single().parentScope() in scopes
        }
        .mapNotNull { contributedSubcomponent ->
          contributedSubcomponent.classId
            .generatedAnvilSubcomponent(clazz.classId)
            .createNestedClassId(Name.identifier(PARENT_COMPONENT))
            .classDeclarationOrNull(resolver)
        }
    }
  }
}
