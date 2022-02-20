package com.squareup.anvil.compiler

import com.squareup.anvil.compiler.internal.argumentType
import com.squareup.anvil.compiler.internal.classDescriptor
import com.squareup.anvil.compiler.internal.reference.ClassReference
import com.squareup.anvil.compiler.internal.reference.toClassReference
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.PackageViewDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import kotlin.LazyThreadSafetyMode.NONE

internal class ClassScanner {

  /**
   * Returns a sequence of contributed classes from the dependency graph. Note that the result
   * includes inner classes already.
   */
  fun findContributedClasses(
    module: ModuleDescriptor,
    packageName: String,
    annotation: FqName,
    scope: FqName?
  ): Sequence<ClassReference.Descriptor> {
    val packageDescriptor = module.getPackage(FqName(packageName))
    return generateSequence(listOf(packageDescriptor)) { subPackages ->
      subPackages
        .flatMap { it.subPackages() }
        .ifEmpty { null }
    }
      .flatMap { it.asSequence() }
      .flatMap {
        it.memberScope.getContributedDescriptors(DescriptorKindFilter.VALUES)
          .asSequence()
      }
      .filterIsInstance<PropertyDescriptor>()
      .groupBy { property ->
        // For each contributed hint there are several properties, e.g. the reference itself
        // and the scope. Group them by their common name without the suffix.
        val name = property.name.asString()
        val suffix = propertySuffixes.firstOrNull { name.endsWith(it) } ?: return@groupBy name
        name.substringBeforeLast(suffix)
      }
      .values
      .asSequence()
      .filter { properties ->
        // Double check that the number of properties matches how many suffixes we have and how
        // many properties we expect.
        properties.size == propertySuffixes.size
      }
      .map { ContributedHint(it) }
      .let { sequence ->
        if (scope != null) {
          sequence
            .filter { hint ->
              // The scope must match what we're looking for.
              hint.scope.fqNameSafe == scope
            }
        } else {
          sequence
        }
      }
      .map { hint -> hint.reference.toClassReference(module) }
      .filter { clazz ->
        // Check that the annotation really is present. It should always be the case, but it's
        // a safetynet in case the generated properties are out of sync.
        clazz.annotations.any {
          it.fqName == annotation && (scope == null || it.scope().fqName == scope)
        }
      }
  }
}

private fun PackageViewDescriptor.subPackages(): List<PackageViewDescriptor> = memberScope
  .getContributedDescriptors(DescriptorKindFilter.PACKAGES)
  .filterIsInstance<PackageViewDescriptor>()

private class ContributedHint(properties: List<PropertyDescriptor>) {
  val reference by lazy(NONE) {
    properties
      .bySuffix(REFERENCE_SUFFIX)
      .toClassDescriptor()
  }

  val scope by lazy(NONE) {
    properties
      .bySuffix(SCOPE_SUFFIX)
      .toClassDescriptor()
  }

  private fun List<PropertyDescriptor>.bySuffix(suffix: String): PropertyDescriptor = first {
    it.name.asString()
      .endsWith(suffix)
  }

  private fun PropertyDescriptor.toClassDescriptor(): ClassDescriptor =
    type.argumentType().classDescriptor()
}
