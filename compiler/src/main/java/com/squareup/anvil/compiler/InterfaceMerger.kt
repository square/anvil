package com.squareup.anvil.compiler

import com.squareup.anvil.compiler.codegen.atLeastOneAnnotation
import com.squareup.anvil.compiler.codegen.find
import com.squareup.anvil.compiler.codegen.generatedAnvilSubcomponent
import com.squareup.anvil.compiler.codegen.parentScope
import com.squareup.anvil.compiler.codegen.reference.RealAnvilModuleDescriptor
import com.squareup.anvil.compiler.internal.classDescriptor
import com.squareup.anvil.compiler.internal.reference.AnvilCompilationExceptionClassReference
import com.squareup.anvil.compiler.internal.reference.ClassReference
import com.squareup.anvil.compiler.internal.reference.Visibility.PUBLIC
import com.squareup.anvil.compiler.internal.reference.allSuperTypeClassReferences
import com.squareup.anvil.compiler.internal.reference.toClassReference
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.resolve.extensions.SyntheticResolveExtension
import org.jetbrains.kotlin.types.KotlinType

/**
 * Finds all contributed component interfaces and adds them as super types to Dagger components
 * annotated with `@MergeComponent` or `@MergeSubcomponent`.
 */
internal class InterfaceMerger(
  private val classScanner: ClassScanner,
  private val moduleDescriptorFactory: RealAnvilModuleDescriptor.Factory
) : SyntheticResolveExtension {
  override fun addSyntheticSupertypes(
    thisDescriptor: ClassDescriptor,
    supertypes: MutableList<KotlinType>
  ) {
    if (thisDescriptor.shouldIgnore()) return

    val module = moduleDescriptorFactory.create(thisDescriptor.module)
    val mergeAnnotatedClass = thisDescriptor.toClassReference(module)
    val mergeAnnotation = mergeAnnotatedClass.annotations.find(mergeComponentFqName).singleOrNull()
      ?: mergeAnnotatedClass.annotations.find(mergeSubcomponentFqName).singleOrNull()
      ?: mergeAnnotatedClass.annotations.find(mergeInterfacesFqName).singleOrNull()

    if (mergeAnnotation == null) {
      super.addSyntheticSupertypes(thisDescriptor, supertypes)
      return
    }

    val mergeScope = mergeAnnotation.scope()

    if (!mergeAnnotatedClass.isInterface()) {
      throw AnvilCompilationExceptionClassReference(
        mergeAnnotatedClass,
        "Dagger components must be interfaces."
      )
    }

    val classes = classScanner
      .findContributedClasses(
        module = module,
        packageName = HINT_CONTRIBUTES_PACKAGE_PREFIX,
        annotation = contributesToFqName,
        scope = mergeScope.fqName
      )
      .filter {
        it.isInterface() && it.annotations.find(daggerModuleFqName).singleOrNull() == null
      }
      .mapNotNull {
        it.annotations
          .find(annotationName = contributesToFqName, scopeName = mergeScope.fqName)
          .singleOrNull()
      }
      .onEach { contributeAnnotation ->
        val contributedClass = contributeAnnotation.declaringClass()
        if (contributedClass.visibility() != PUBLIC) {
          throw AnvilCompilationExceptionClassReference(
            contributedClass,
            "${contributedClass.fqName} is contributed to the Dagger graph, but the " +
              "interface is not public. Only public interfaces are supported."
          )
        }
      }
      // Convert the sequence to a list to avoid iterating it twice. We use the result twice
      // for replaced classes and the final result.
      .toList()

    val replacedClasses = classes
      .flatMap { contributeAnnotation ->
        val contributedClass = contributeAnnotation.declaringClass()
        contributedClass
          .atLeastOneAnnotation(contributeAnnotation.fqName)
          .single()
          .replaces()
          .onEach { classToReplace ->
            // Verify the other class is an interface. It doesn't make sense for a contributed
            // interface to replace a class that is not an interface.
            if (!classToReplace.isInterface()) {
              throw AnvilCompilationExceptionClassReference(
                contributedClass,
                "${contributedClass.fqName} wants to replace " +
                  "${classToReplace.fqName}, but the class being " +
                  "replaced is not an interface."
              )
            }

            val contributesToAnnotation = classToReplace
              .annotations.find(contributesToFqName).singleOrNull()
            val contributesBindingAnnotation = classToReplace
              .annotations.find(contributesBindingFqName).singleOrNull()
            val contributesMultibindingAnnotation = classToReplace
              .annotations.find(contributesMultibindingFqName).singleOrNull()

            // Verify that the replaced classes use the same scope.
            val scopeOfReplacement = contributesToAnnotation?.scope()
              ?: contributesBindingAnnotation?.scope()
              ?: contributesMultibindingAnnotation?.scope()
              ?: throw AnvilCompilationExceptionClassReference(
                contributedClass,
                "Could not determine the scope of the replaced class " +
                  "${classToReplace.fqName}."
              )

            if (scopeOfReplacement != mergeScope) {
              throw AnvilCompilationExceptionClassReference(
                contributedClass,
                "${contributedClass.fqName} with scope ${mergeScope.fqName} wants to replace " +
                  "${classToReplace.fqName} with scope " +
                  "${scopeOfReplacement.fqName}. The replacement must use the same " +
                  "scope."
              )
            }
          }
      }
      .toSet()

    val excludedClasses = mergeAnnotation.exclude()
      .filter { it.isInterface() }
      .onEach { excludedClass ->
        val contributesToAnnotation = excludedClass
          .annotations.find(contributesToFqName).singleOrNull()
        val contributesBindingAnnotation = excludedClass
          .annotations.find(contributesBindingFqName).singleOrNull()
        val contributesMultibindingAnnotation = excludedClass
          .annotations.find(contributesMultibindingFqName).singleOrNull()
        val contributesSubcomponentAnnotation = excludedClass
          .annotations.find(contributesSubcomponentFqName).singleOrNull()

        // Verify that the replaced classes use the same scope.
        val scopeOfExclusion = contributesToAnnotation?.scope()
          ?: contributesBindingAnnotation?.scope()
          ?: contributesMultibindingAnnotation?.scope()
          ?: contributesSubcomponentAnnotation?.parentScope()
          ?: throw AnvilCompilationExceptionClassReference(
            mergeAnnotatedClass,
            "Could not determine the scope of the excluded class " +
              "${excludedClass.fqName}."
          )

        if (scopeOfExclusion.fqName != mergeScope.fqName) {
          throw AnvilCompilationExceptionClassReference(
            mergeAnnotatedClass,
            "${mergeAnnotatedClass.fqName} with scope ${mergeScope.fqName} wants to exclude " +
              "${excludedClass.fqName} with scope " +
              "${scopeOfExclusion.fqName}. The exclusion must use the same scope."
          )
        }
      }

    if (excludedClasses.isNotEmpty()) {
      val intersect = supertypes
        .map { it.classDescriptor().toClassReference(module) }
        .flatMap { it.allSuperTypeClassReferences(includeSelf = true) }
        .intersect(excludedClasses.toSet())

      if (intersect.isNotEmpty()) {
        throw AnvilCompilationExceptionClassReference(
          classReference = mergeAnnotatedClass,
          message = "${mergeAnnotatedClass.clazz.name} excludes types that it implements or " +
            "extends. These types cannot be excluded. Look at all the super types to find these " +
            "classes: ${intersect.joinToString { it.fqName.asString() }}"
        )
      }
    }

    @Suppress("ConvertCallChainIntoSequence")
    val contributedClasses = classes
      .map { it.declaringClass() }
      .filterNot {
        replacedClasses.contains(it) || excludedClasses.contains(it)
      }
      .plus(findContributedSubcomponentParentInterfaces(mergeAnnotatedClass, mergeScope, module))
      // Avoids an error for repeated interfaces.
      .distinct()
      .map { it.clazz.defaultType }

    supertypes += contributedClasses
    super.addSyntheticSupertypes(mergeAnnotatedClass.clazz, supertypes)
  }

  private fun findContributedSubcomponentParentInterfaces(
    clazz: ClassReference,
    scope: ClassReference,
    module: ModuleDescriptor
  ): Sequence<ClassReference.Descriptor> {
    return classScanner
      .findContributedClasses(
        module = module,
        packageName = HINT_SUBCOMPONENTS_PACKAGE_PREFIX,
        annotation = contributesSubcomponentFqName,
        scope = null
      )
      .filter {
        it.atLeastOneAnnotation(contributesSubcomponentFqName).single().parentScope() == scope
      }
      .mapNotNull { contributedSubcomponent ->
        contributedSubcomponent.classId
          .generatedAnvilSubcomponent(clazz.classId)
          .createNestedClassId(Name.identifier(PARENT_COMPONENT))
          .classReferenceOrNull(module)
      }
  }
}
