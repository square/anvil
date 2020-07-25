package com.squareup.anvil.compiler

import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.EffectiveVisibility.Public
import org.jetbrains.kotlin.descriptors.effectiveVisibility
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.DescriptorUtils.getClassDescriptorForType
import org.jetbrains.kotlin.resolve.DescriptorUtils.getFqNameSafe
import org.jetbrains.kotlin.resolve.constants.ArrayValue
import org.jetbrains.kotlin.resolve.constants.KClassValue
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.resolve.extensions.SyntheticResolveExtension
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.supertypes

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
    val mergeAnnotation = thisDescriptor.findAnnotation(mergeComponentFqName)
        ?: thisDescriptor.findAnnotation(mergeSubcomponentFqName)
        ?: thisDescriptor.findAnnotation(mergeInterfacesFqName)

    if (mergeAnnotation == null) {
      super.addSyntheticSupertypes(thisDescriptor, supertypes)
      return
    }

    val scope = mergeAnnotation.scope(thisDescriptor.module)

    if (!DescriptorUtils.isInterface(thisDescriptor)) {
      throw AnvilCompilationException(thisDescriptor, "Dagger components must be interfaces.")
    }

    val classes = classScanner
        .findContributedClasses(thisDescriptor.module)
        .asSequence()
        .filter {
          DescriptorUtils.isInterface(it) && it.findAnnotation(daggerModuleFqName) == null
        }
        .mapNotNull {
          val contributeAnnotation =
            it.findAnnotation(contributesToFqName, scope = scope) ?: return@mapNotNull null
          it to contributeAnnotation
        }
        .onEach { (classDescriptor, _) ->
          if (classDescriptor.effectiveVisibility() !is Public) {
            throw AnvilCompilationException(
                classDescriptor,
                "${classDescriptor.fqNameSafe} is contributed to the Dagger graph, but the " +
                    "interface is not public. Only public interfaces are supported."
            )
          }
        }

    val replacedClasses = classes
        .mapNotNull { (classDescriptor, contributeAnnotation) ->
          val kClassValue = contributeAnnotation.getAnnotationValue("replaces")
              as? KClassValue ?: return@mapNotNull null

          // Verify the other class is an interface. It doesn't make sense for a contributed interface
          // to replace a class that is not an interface.
          val kotlinType = kClassValue.getType(classDescriptor.module)
              .argumentType()
          val classDescriptorForReplacement = getClassDescriptorForType(kotlinType)
          if (!DescriptorUtils.isInterface(classDescriptorForReplacement)) {
            throw AnvilCompilationException(
                classDescriptor,
                "${classDescriptor.fqNameSafe} wants to replace " +
                    "${classDescriptorForReplacement.fqNameSafe}, but the class being replaced is " +
                    "not an interface."
            )
          }

          classDescriptorForReplacement.fqNameSafe
        }
        .toSet()

    val excludedClasses = (mergeAnnotation.getAnnotationValue("exclude") as? ArrayValue)
        ?.value
        ?.map {
          getClassDescriptorForType(
              it.getType(thisDescriptor.module)
                  .argumentType()
          )
        }
        ?.filter { DescriptorUtils.isInterface(it) }
        ?.map { it.fqNameSafe }
        ?: emptyList()

    if (excludedClasses.isNotEmpty()) {
      val intersect = supertypes.getAllSuperTypes()
          .intersect(excludedClasses)

      if (intersect.isNotEmpty()) {
        throw AnvilCompilationException(
            classDescriptor = thisDescriptor,
            message = "${thisDescriptor.name} excludes types that it implements or extends. " +
                "These types cannot be excluded. Look at all the super types to find these " +
                "classes: ${intersect.joinToString()}"
        )
      }
    }

    val contributedClasses = classes
        .map { it.first }
        .filterNot {
          val fqName = it.fqNameSafe
          replacedClasses.contains(fqName) || excludedClasses.contains(fqName)
        }
        // Avoids an error for repeated interfaces.
        .distinctBy { it.fqNameSafe }
        .map { it.defaultType }

    supertypes += contributedClasses
    super.addSyntheticSupertypes(thisDescriptor, supertypes)
  }

  private fun List<KotlinType>.getAllSuperTypes(): List<FqName> =
    generateSequence(this) { kotlinTypes ->
      if (kotlinTypes.isEmpty()) null else kotlinTypes.flatMap { it.supertypes() }
    }
        .flatMap { it.asSequence() }
        .map { getFqNameSafe(getClassDescriptorForType(it)) }
        .toList()
}
