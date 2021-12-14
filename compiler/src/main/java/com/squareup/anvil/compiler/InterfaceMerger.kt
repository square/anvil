package com.squareup.anvil.compiler

import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.codegen.generatedAnvilSubcomponent
import com.squareup.anvil.compiler.internal.annotations
import com.squareup.anvil.compiler.internal.argumentType
import com.squareup.anvil.compiler.internal.classDescriptorOrNull
import com.squareup.anvil.compiler.internal.getAllSuperTypes
import com.squareup.anvil.compiler.internal.getAnnotationValue
import com.squareup.anvil.compiler.internal.parentScope
import com.squareup.anvil.compiler.internal.requireClassDescriptor
import com.squareup.anvil.compiler.internal.requireClassId
import com.squareup.anvil.compiler.internal.requireFqName
import com.squareup.anvil.compiler.internal.scope
import com.squareup.anvil.compiler.internal.singleOrEmpty
import com.squareup.anvil.compiler.internal.takeIfNotEmpty
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.EffectiveVisibility.Public
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.effectiveVisibility
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.constants.ArrayValue
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.resolve.extensions.SyntheticResolveExtension
import org.jetbrains.kotlin.types.KotlinType

/**
 * Finds all contributed component interfaces and adds them as super types to Dagger components
 * annotated with `@MergeComponent` or `@MergeSubcomponent`.
 */
internal class InterfaceMerger(
  private val classScanner: ClassScanner
) : SyntheticResolveExtension {
  override fun addSyntheticSupertypes(
    thisDescriptor: ClassDescriptor,
    supertypes: MutableList<KotlinType>
  ) {
    val mergeAnnotations = thisDescriptor.annotations(mergeComponentFqName).takeIfNotEmpty()
      ?: thisDescriptor.annotations(mergeSubcomponentFqName).takeIfNotEmpty()
      ?: thisDescriptor.annotations(mergeInterfacesFqName).takeIfNotEmpty()

    if (mergeAnnotations == null) {
      super.addSyntheticSupertypes(thisDescriptor, supertypes)
      return
    }

    val mergeAnnotation = mergeAnnotations.singleOrNull()
      ?: throw AnvilCompilationException(
        classDescriptor = thisDescriptor,
        message = "Expected only a single annotation, but found multiple " +
          "${mergeAnnotations.first().requireFqName()} annotations."
      )

    if (!DescriptorUtils.isInterface(thisDescriptor)) {
      throw AnvilCompilationException(thisDescriptor, "Dagger components must be interfaces.")
    }

    val module = thisDescriptor.module

    val scope = try {
      mergeAnnotation.scope(module)
    } catch (e: AssertionError) {
      // In some scenarios this error is thrown. Throw a new exception with a better explanation.
      // Caused by: java.lang.AssertionError: Recursion detected in a lazy value under
      // LockBasedStorageManager@420989af (TopDownAnalyzer for JVM)
      throw AnvilCompilationException(
        classDescriptor = thisDescriptor,
        message = "It seems like you tried to contribute an inner class to its outer class. " +
          "This is not supported and results in a compiler error.",
        cause = e
      )
    }
    val scopeFqName = scope.fqNameSafe

    val classes = classScanner
      .findContributedHints(
        module = module,
        annotation = contributesToFqName,
      )
      .filter { hint ->
        DescriptorUtils.isInterface(hint.descriptor) &&
          hint.isContributedToScope(scopeFqName) &&
          hint.descriptor.annotations(daggerModuleFqName).singleOrEmpty() == null
      }
      .onEach { hint ->
        if (hint.descriptor.effectiveVisibility() !is Public) {
          throw AnvilCompilationException(
            hint.descriptor,
            "${hint.fqName} is contributed to the Dagger graph, but the " +
              "interface is not public. Only public interfaces are supported."
          )
        }
      }
      // Convert the sequence to a list to avoid iterating it twice. We use the result twice
      // for replaced classes and the final result.
      .toList()

    val replacedClasses = classes
      .flatMap { hint ->
        hint.contributedAnnotation(scopeFqName).replaces(hint.descriptor.module)
          .onEach { classDescriptorForReplacement ->
            // Verify the other class is an interface. It doesn't make sense for a contributed
            // interface to replace a class that is not an interface.
            if (!DescriptorUtils.isInterface(classDescriptorForReplacement)) {
              throw AnvilCompilationException(
                classDescriptor = hint.descriptor,
                message = "${hint.fqName} wants to replace " +
                  "${classDescriptorForReplacement.fqNameSafe}, but the class being " +
                  "replaced is not an interface."
              )
            }

            // Verify that the replaced classes use the same scope.
            val contributesToOurScope = classDescriptorForReplacement
              .annotations(hint.annotationFqName)
              .any { it.scope(module).fqNameSafe == scopeFqName }

            if (!contributesToOurScope) {
              throw AnvilCompilationException(
                classDescriptor = hint.descriptor,
                message = "${hint.fqName} with scope $scopeFqName wants to replace " +
                  "${classDescriptorForReplacement.fqNameSafe}, but the replaced class isn't " +
                  "contributed to the same scope."
              )
            }
          }
          .map { it.fqNameSafe }
      }
      .toSet()

    val excludedClasses = (mergeAnnotation.getAnnotationValue("exclude") as? ArrayValue)
      ?.value
      ?.map { it.argumentType(module).requireClassDescriptor() }
      ?.map { classDescriptorForExclusion ->
        val scopes = classDescriptorForExclusion
          .annotations(contributesToFqName).map { it.scope(module) } +
          classDescriptorForExclusion
            .annotations(contributesBindingFqName).map { it.scope(module) } +
          classDescriptorForExclusion
            .annotations(contributesMultibindingFqName).map { it.scope(module) } +
          classDescriptorForExclusion
            .annotations(contributesSubcomponentFqName).map { it.parentScope(module) }

        // Verify that the excluded classes use the same scope.
        val contributesToOurScope = scopes.any { it.fqNameSafe == scopeFqName }

        if (!contributesToOurScope) {
          throw AnvilCompilationException(
            thisDescriptor,
            "${thisDescriptor.fqNameSafe} with scope $scopeFqName wants to exclude " +
              "${classDescriptorForExclusion.fqNameSafe}, but the excluded class isn't " +
              "contributed to the same scope."
          )
        }

        classDescriptorForExclusion.fqNameSafe
      }
      ?: emptyList()

    if (excludedClasses.isNotEmpty()) {
      val intersect = supertypes.getAllSuperTypes()
        .toList()
        .intersect(excludedClasses.toSet())

      if (intersect.isNotEmpty()) {
        throw AnvilCompilationException(
          classDescriptor = thisDescriptor,
          message = "${thisDescriptor.name} excludes types that it implements or extends. " +
            "These types cannot be excluded. Look at all the super types to find these " +
            "classes: ${intersect.joinToString()}"
        )
      }
    }

    @Suppress("ConvertCallChainIntoSequence")
    val contributedClasses = classes
      .map { it.descriptor }
      .filterNot {
        val fqName = it.fqNameSafe
        fqName in replacedClasses || fqName in excludedClasses
      }
      .plus(findContributedSubcomponentParentInterfaces(thisDescriptor, scopeFqName, module))
      // Avoids an error for repeated interfaces.
      .distinctBy { it.fqNameSafe }
      .map { it.defaultType }

    supertypes += contributedClasses
    super.addSyntheticSupertypes(thisDescriptor, supertypes)
  }

  private fun findContributedSubcomponentParentInterfaces(
    descriptor: ClassDescriptor,
    scope: FqName,
    module: ModuleDescriptor
  ): Sequence<ClassDescriptor> {
    return classScanner
      .findContributedHints(
        module = module,
        annotation = contributesSubcomponentFqName
      )
      .filter { contributedHint ->
        contributedHint
          .contributedAnnotations(scope = null)
          .map { it.parentScope(module).fqNameSafe }
          .any { it == scope }
      }
      .mapNotNull { contributedHint ->
        contributedHint.descriptor.requireClassId()
          .generatedAnvilSubcomponent(descriptor.requireClassId())
          .createNestedClassId(Name.identifier(PARENT_COMPONENT))
          .classDescriptorOrNull(module)
      }
  }
}
