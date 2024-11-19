package com.squareup.anvil.compiler

import com.squareup.anvil.compiler.codegen.atLeastOneAnnotation
import com.squareup.anvil.compiler.codegen.find
import com.squareup.anvil.compiler.codegen.findAll
import com.squareup.anvil.compiler.codegen.generatedAnvilSubcomponentClassId
import com.squareup.anvil.compiler.codegen.parentScope
import com.squareup.anvil.compiler.codegen.reference.RealAnvilModuleDescriptor
import com.squareup.anvil.compiler.internal.classDescriptor
import com.squareup.anvil.compiler.internal.reference.AnvilCompilationExceptionClassReference
import com.squareup.anvil.compiler.internal.reference.ClassReference
import com.squareup.anvil.compiler.internal.reference.Visibility
import com.squareup.anvil.compiler.internal.reference.allSuperTypeClassReferences
import com.squareup.anvil.compiler.internal.reference.toClassReference
import com.squareup.anvil.compiler.internal.reference.toClassReferenceOrNull
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.descriptorUtil.classId
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.resolve.extensions.SyntheticResolveExtension
import org.jetbrains.kotlin.types.KotlinType

/**
 * Finds all contributed component interfaces and adds them as super types to Dagger components
 * annotated with `@MergeComponent` or `@MergeSubcomponent`.
 */
internal class SyntheticContributionMerger(
  private val classScanner: ClassScanner,
  private val moduleDescriptorFactory: RealAnvilModuleDescriptor.Factory,
) : SyntheticResolveExtension {

  override fun generateSyntheticProperties(
    thisDescriptor: ClassDescriptor,
    name: Name,
    bindingContext: BindingContext,
    fromSupertypes: ArrayList<PropertyDescriptor>,
    result: MutableSet<PropertyDescriptor>,
  ) {
    if (thisDescriptor.shouldIgnore()) return
    classScanner.findContributedClasses(
      module = thisDescriptor.module,
      annotation = contributesToFqName,
      scope = null,
    )
      .let {
      }
    super.generateSyntheticProperties(thisDescriptor, name, bindingContext, fromSupertypes, result)
  }

  // If we're evaluating an anonymous inner class, it cannot merge anything and will cause
  // a failure if we try to resolve its [ClassId]
  private fun ClassDescriptor.shouldIgnore(): Boolean {
    return classId == null || DescriptorUtils.isAnonymousObject(this)
  }

  override fun addSyntheticSupertypes(
    thisDescriptor: ClassDescriptor,
    supertypes: MutableList<KotlinType>,
  ) {
    if (thisDescriptor.shouldIgnore()) return

    val module = moduleDescriptorFactory.create(thisDescriptor.module)
    val mergeAnnotatedClass = thisDescriptor.toClassReference(module)

    val mergeAnnotations = mergeAnnotatedClass.annotations
      .findAll(mergeComponentFqName, mergeSubcomponentFqName, mergeInterfacesFqName)
      .ifEmpty { return }

    if (!mergeAnnotatedClass.isInterface()) {
      throw AnvilCompilationExceptionClassReference(
        classReference = mergeAnnotatedClass,
        message = "Dagger components must be interfaces.",
      )
    }

    val scopes = mergeAnnotations.map {
      try {
        it.scope()
      } catch (e: AssertionError) {
        // In some scenarios this error is thrown. Throw a new exception with a better explanation.
        // Caused by: java.lang.AssertionError: Recursion detected in a lazy value under
        // LockBasedStorageManager@420989af (TopDownAnalyzer for JVM)
        throw AnvilCompilationExceptionClassReference(
          classReference = mergeAnnotatedClass,
          message = "It seems like you tried to contribute an inner class to its outer class. " +
            "This is not supported and results in a compiler error.",
          cause = e,
        )
      }
    }

    val contributesAnnotations = mergeAnnotations
      .flatMap { annotation ->
        classScanner
          .findContributedClasses(
            module = module,
            annotation = contributesToFqName,
            scope = annotation.scope().fqName,
          )
      }
      .filter { clazz ->
        clazz.isInterface() && clazz.annotations.find(daggerModuleFqName).singleOrNull() == null
      }
      .flatMap { clazz ->
        clazz.annotations
          .find(annotationName = contributesToFqName)
          .filter { it.scope() in scopes }
      }
      .onEach { contributeAnnotation ->
        val contributedClass = contributeAnnotation.declaringClass()
        if (contributedClass.visibility() != Visibility.PUBLIC) {
          throw AnvilCompilationExceptionClassReference(
            classReference = contributedClass,
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
          .atLeastOneAnnotation(contributeAnnotation.fqName)
          .flatMap { it.replaces() }
          .onEach { classToReplace ->
            // Verify the other class is an interface. It doesn't make sense for a contributed
            // interface to replace a class that is not an interface.
            if (!classToReplace.isInterface()) {
              throw AnvilCompilationExceptionClassReference(
                classReference = contributedClass,
                message = "${contributedClass.fqName} wants to replace " +
                  "${classToReplace.fqName}, but the class being " +
                  "replaced is not an interface.",
              )
            }

            val contributesToOurScope = classToReplace.annotations
              .findAll(
                contributesToFqName,
                contributesBindingFqName,
                contributesMultibindingFqName,
              )
              .map { it.scope() }
              .any { scope -> scope in scopes }

            if (!contributesToOurScope) {
              throw AnvilCompilationExceptionClassReference(
                classReference = contributedClass,
                message = "${contributedClass.fqName} with scopes " +
                  "${
                    scopes.joinToString(
                      prefix = "[",
                      postfix = "]",
                    ) { it.fqName.asString() }
                  } " +
                  "wants to replace ${classToReplace.fqName}, but the replaced class isn't " +
                  "contributed to the same scope.",
              )
            }
          }
      }
      .toSet()

    val excludedClasses = mergeAnnotations
      .flatMap { it.exclude() }
      .filter { it.isInterface() }
      .onEach { excludedClass ->
        // Verify that the replaced classes use the same scope.
        val contributesToOurScope = excludedClass.annotations
          .findAll(contributesToFqName, contributesBindingFqName, contributesMultibindingFqName)
          .map { it.scope() }
          .plus(
            excludedClass.annotations.find(contributesSubcomponentFqName).map { it.parentScope() },
          )
          .any { scope -> scope in scopes }

        if (!contributesToOurScope) {
          throw AnvilCompilationExceptionClassReference(
            message = "${mergeAnnotatedClass.fqName} with scopes " +
              "${scopes.joinToString(prefix = "[", postfix = "]") { it.fqName.asString() }} " +
              "wants to exclude ${excludedClass.fqName}, but the excluded class isn't " +
              "contributed to the same scope.",
            classReference = mergeAnnotatedClass,
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
          message = "${mergeAnnotatedClass.fqName} excludes types that it implements or " +
            "extends. These types cannot be excluded. Look at all the super types to find these " +
            "classes: ${intersect.joinToString { it.fqName.asString() }}.",
        )
      }
    }

    @Suppress("ConvertCallChainIntoSequence")
    supertypes += contributesAnnotations
      .map { it.declaringClass() }
      .filter { clazz ->
        clazz !in replacedClasses && clazz !in excludedClasses
      }
      .plus(findContributedSubcomponentParentInterfaces(mergeAnnotatedClass, scopes, module))
      // Avoids an error for repeated interfaces.
      .distinct()
      .map { it.clazz.defaultType }
  }

  private fun findContributedSubcomponentParentInterfaces(
    clazz: ClassReference,
    scopes: Collection<ClassReference>,
    module: ModuleDescriptor,
  ): Sequence<ClassReference.Descriptor> {
    return classScanner
      .findContributedClasses(
        module = module,
        annotation = contributesSubcomponentFqName,
        scope = null,
      )
      .filter {
        it.atLeastOneAnnotation(contributesSubcomponentFqName).single().parentScope() in scopes
      }
      .mapNotNull { contributedSubcomponent ->
        contributedSubcomponent.classId
          .generatedAnvilSubcomponentClassId(clazz.classId)
          .createNestedClassId(Name.identifier(PARENT_COMPONENT))
          .classReferenceOrNull(module)
      }
  }

  @Suppress("UNCHECKED_CAST")
  private fun <T : ClassReference> ClassId.classReferenceOrNull(
    module: ModuleDescriptor,
  ): T? = asSingleFqName().toClassReferenceOrNull(module) as T?
}
