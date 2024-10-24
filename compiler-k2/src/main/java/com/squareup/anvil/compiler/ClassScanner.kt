package com.squareup.anvil.compiler

import com.squareup.anvil.compiler.GeneratedProperty.ReferenceProperty
import com.squareup.anvil.compiler.GeneratedProperty.ScopeProperty
import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.internal.argumentType
import com.squareup.anvil.compiler.internal.classDescriptor
import com.squareup.anvil.compiler.internal.reference.ClassReference
import com.squareup.anvil.compiler.internal.reference.toClassReference
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.PackageViewDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter

internal class ClassScanner {

  private val cache = mutableMapOf<CacheKey, Collection<List<GeneratedProperty>>>()

  /**
   * Returns a sequence of contributed classes from the dependency graph. Note that the result
   * includes inner classes already.
   */
  fun findContributedClasses(
    module: ModuleDescriptor,
    annotation: FqName,
    scope: FqName?,
  ): Sequence<ClassReference.Descriptor> {
    val propertyGroups = cache.getOrPut(CacheKey(annotation, module.hashCode())) {
      module.getPackage(
        FqName(HINT_PACKAGE),
      ).memberScope.getContributedDescriptors(DescriptorKindFilter.VALUES)
        .filterIsInstance<PropertyDescriptor>()
        .mapNotNull { GeneratedProperty.fromDescriptor(it) }
        .groupBy { property -> property.baseName }
        .values
    }

    return propertyGroups
      .asSequence()
      .mapNotNull { properties ->
        val reference = properties.filterIsInstance<ReferenceProperty>()
          // In some rare cases we can see a generated property for the same identifier.
          // Filter them just in case, see https://github.com/square/anvil/issues/460 and
          // https://github.com/square/anvil/issues/565
          .distinctBy { it.baseName }
          .singleOrEmpty()
          ?: throw AnvilCompilationException(
            message = "Couldn't find the reference for a generated hint: ${properties[0].baseName}.",
          )

        val scopes = properties.filterIsInstance<ScopeProperty>()
          .ifEmpty {
            throw AnvilCompilationException(
              message = "Couldn't find any scope for a generated hint: ${properties[0].baseName}.",
            )
          }
          .map { it.descriptor.type.argumentType().classDescriptor().fqNameSafe }

        // Look for the right scope even before resolving the class and resolving all its super
        // types.
        if (scope != null && scope !in scopes) return@mapNotNull null

        reference.descriptor.type.argumentType()
          .classDescriptor()
          .toClassReference(module)
      }
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

private sealed class GeneratedProperty(
  val descriptor: PropertyDescriptor,
  val baseName: String,
) {
  class ReferenceProperty(
    descriptor: PropertyDescriptor,
    baseName: String,
  ) : GeneratedProperty(descriptor, baseName)

  class ScopeProperty(
    descriptor: PropertyDescriptor,
    baseName: String,
  ) : GeneratedProperty(descriptor, baseName)

  companion object {
    fun fromDescriptor(descriptor: PropertyDescriptor): GeneratedProperty? {
      // For each contributed hint there are several properties, e.g. the reference itself
      // and the scopes. Group them by their common name without the suffix.
      val name = descriptor.name.asString()

      return when {
        name.endsWith(REFERENCE_SUFFIX) ->
          ReferenceProperty(descriptor, name.substringBeforeLast(REFERENCE_SUFFIX))
        name.contains(SCOPE_SUFFIX) -> {
          // The old scope hint didn't have a number. Now that there can be multiple scopes
          // we append a number for all scopes, but we still need to support the old format.
          val indexString = name.substringAfterLast(SCOPE_SUFFIX)
          if (indexString.toIntOrNull() != null || indexString.isEmpty()) {
            ScopeProperty(descriptor, name.substringBeforeLast(SCOPE_SUFFIX))
          } else {
            null
          }
        }
        else -> null
      }
    }
  }
}

private data class CacheKey(
  val fqName: FqName,
  val moduleHash: Int,
)
