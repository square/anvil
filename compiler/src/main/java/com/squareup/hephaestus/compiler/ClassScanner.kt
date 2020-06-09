package com.squareup.hephaestus.compiler

import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.PackageViewDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter

internal class ClassScanner(
  private val additionalPackages: Collection<String>
) {

  private val cache = mutableMapOf<String, List<ClassDescriptor>>()

  /**
   * Returns a list of classes from the dependency graph annotated with `@ContributesTo`. Note that
   * the list includes inner classes already. It does NOT contain classes having this annotation from
   * this module. Use [findTopLevelClassesInThisModule] for this purpose instead.
   */
  fun findContributedClassesInDependencyGraph(
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
          .filter { it.findAnnotation(contributesToFqName) != null }
          .toList()
    }
  }

  /**
   * Returns a list of top level classes (meaning no inner classes) in this module. Note that the
   * algorithm for this method takes all used packages from this module and lists all classes within
   * this package. That means the returned list could include classes from module dependencies in the
   * same package.
   */
  fun findTopLevelClassesInThisModule(
    module: ModuleDescriptor
  ): List<ClassDescriptor> {
    return additionalPackages
        .flatMap { packageString ->
          cache.getOrPut(packageString) {
            additionalPackages
                .map { module.getPackage(FqName(packageString)) }
                .flatMap { packageDescriptor ->
                  packageDescriptor
                      .memberScope
                      .getContributedDescriptors(DescriptorKindFilter.CLASSIFIERS) { name ->
                        val nameString = name.asString()
                        nameString != "R" && nameString != "BuildConfig"
                      }
                }
                .filterIsInstance<ClassDescriptor>()
                .filterNot {
                  // Exclude generated Dagger components. There's no need to look at them. Interestingly,
                  // they cause some recursive errors when compiling inner classes.
                  it.isGeneratedDaggerComponent()
                }
          }
        }
  }

  /**
   * Returns all inner classes for the given descriptor. The result list does NOT contain [descriptor]
   * itself.
   */
  fun innerClasses(descriptor: ClassDescriptor): List<ClassDescriptor> {
    return cache.getOrPut(descriptor.fqNameSafe.asString()) {
      descriptor.unsubstitutedInnerClassesScope
          .getContributedDescriptors(DescriptorKindFilter.CLASSIFIERS)
          .filterIsInstance<ClassDescriptor>()
    }
  }
}

private fun PackageViewDescriptor.subPackages(): List<PackageViewDescriptor> = memberScope
    .getContributedDescriptors(DescriptorKindFilter.PACKAGES)
    .filterIsInstance<PackageViewDescriptor>()
