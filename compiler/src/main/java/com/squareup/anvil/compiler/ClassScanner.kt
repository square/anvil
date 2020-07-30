package com.squareup.anvil.compiler

import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.PackageViewDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter

internal class ClassScanner {

  private val cache = mutableMapOf<String, List<ClassDescriptor>>()

  /**
   * Returns a list of classes from the dependency graph annotated with `@ContributesTo`. Note that
   * the list includes inner classes already.
   */
  fun findContributedClasses(
    module: ModuleDescriptor,
    packageName: String = HINT_PACKAGE_PREFIX
  ): List<ClassDescriptor> {
    return cache.getOrPut(packageName) {
      val packageDescriptor = module.getPackage(FqName(packageName))
      generateSequence(packageDescriptor.subPackages()) { subPackages ->
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
          .map { DescriptorUtils.getClassDescriptorForType(it.type.argumentType()) }
          .filter { it.annotationOrNull(contributesToFqName) != null }
          .toList()
    }
  }
}

private fun PackageViewDescriptor.subPackages(): List<PackageViewDescriptor> = memberScope
    .getContributedDescriptors(DescriptorKindFilter.PACKAGES)
    .filterIsInstance<PackageViewDescriptor>()
