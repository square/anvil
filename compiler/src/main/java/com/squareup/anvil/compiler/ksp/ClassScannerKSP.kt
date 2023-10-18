package com.squareup.anvil.compiler.ksp

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.impl.ResolverImpl
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.squareup.anvil.compiler.HINT_BINDING_PACKAGE_PREFIX
import com.squareup.anvil.compiler.HINT_CONTRIBUTES_PACKAGE_PREFIX
import com.squareup.anvil.compiler.HINT_MULTIBINDING_PACKAGE_PREFIX
import com.squareup.anvil.compiler.HINT_SUBCOMPONENTS_PACKAGE_PREFIX
import com.squareup.anvil.compiler.REFERENCE_SUFFIX
import com.squareup.anvil.compiler.SCOPE_SUFFIX
import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.codegen.ksp.scope
import com.squareup.anvil.compiler.contributesBindingFqName
import com.squareup.anvil.compiler.contributesMultibindingFqName
import com.squareup.anvil.compiler.contributesSubcomponentFqName
import com.squareup.anvil.compiler.contributesToFqName
import com.squareup.anvil.compiler.ksp.GeneratedProperty.ReferenceProperty
import com.squareup.anvil.compiler.ksp.GeneratedProperty.ScopeProperty
import com.squareup.anvil.compiler.singleOrEmpty
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.PackageViewDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter

@OptIn(KspExperimental::class)
internal class ClassScannerKSP {

  private val cache = mutableMapOf<CacheKey, Collection<List<GeneratedProperty>>>()

  /**
   * Returns a sequence of contributed classes from the dependency graph.
   */
  fun findContributedClasses(
    resolver: Resolver,
    annotation: FqName,
    scope: KSClassDeclaration?
  ): Sequence<KSClassDeclaration> {
    val module = (resolver as ResolverImpl).module
    val propertyGroups = cache.getOrPut(CacheKey(annotation, module.hashCode())) {
      val packageName = when (annotation) {
        contributesToFqName -> HINT_CONTRIBUTES_PACKAGE_PREFIX
        contributesBindingFqName -> HINT_BINDING_PACKAGE_PREFIX
        contributesMultibindingFqName -> HINT_MULTIBINDING_PACKAGE_PREFIX
        contributesSubcomponentFqName -> HINT_SUBCOMPONENTS_PACKAGE_PREFIX
        else -> throw AnvilCompilationException(message = "Cannot find hints for $annotation.")
      }

      module.hintPackages(FqName(packageName))
        .flatMap { resolver.getDeclarationsFromPackage(it) }
        .filterIsInstance<KSPropertyDeclaration>()
        .mapNotNull { GeneratedProperty.fromDeclaration(it) }
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
            message = "Couldn't find the reference for a generated hint: ${properties[0].baseName}."
          )

        val scopes = properties.filterIsInstance<ScopeProperty>()
          .ifEmpty {
            throw AnvilCompilationException(
              message = "Couldn't find any scope for a generated hint: ${properties[0].baseName}."
            )
          }
          .map { it.declaration.type.singleArgumentType.classDeclaration }

        // Look for the right scope even before resolving the class and resolving all its super
        // types.
        if (scope != null && scope !in scopes) return@mapNotNull null

        reference.declaration.type.singleArgumentType
          .classDeclaration
      }
      .filter { clazz ->
        // Check that the annotation really is present. It should always be the case, but it's
        // a safetynet in case the generated properties are out of sync.
        clazz.annotations.any {
          it.annotationType.resolve().classDeclaration.fqName == annotation && (scope == null || it.scope() == scope)
        }
      }
  }

  private fun ModuleDescriptor.hintPackages(annotation: FqName): Sequence<String> {
    val packageName = when (annotation) {
      contributesToFqName -> HINT_CONTRIBUTES_PACKAGE_PREFIX
      contributesBindingFqName -> HINT_BINDING_PACKAGE_PREFIX
      contributesMultibindingFqName -> HINT_MULTIBINDING_PACKAGE_PREFIX
      contributesSubcomponentFqName -> HINT_SUBCOMPONENTS_PACKAGE_PREFIX
      else -> throw AnvilCompilationException(message = "Cannot find hints for $annotation.")
    }

    return generateSequence(listOf(getPackage(FqName(packageName)))) { subPackages ->
      subPackages
        .flatMap { it.subPackages() }
        .ifEmpty { null }
    }
      .flatMap { it.asSequence() }
      .mapNotNull { it.fqNameOrNull()?.asString() }
  }
}

private fun PackageViewDescriptor.subPackages(): List<PackageViewDescriptor> = memberScope
  .getContributedDescriptors(DescriptorKindFilter.PACKAGES)
  .filterIsInstance<PackageViewDescriptor>()

private sealed class GeneratedProperty(
  val declaration: KSPropertyDeclaration,
  val baseName: String
) {
  class ReferenceProperty(
    descriptor: KSPropertyDeclaration,
    baseName: String
  ) : GeneratedProperty(descriptor, baseName)

  class ScopeProperty(
    descriptor: KSPropertyDeclaration,
    baseName: String
  ) : GeneratedProperty(descriptor, baseName)

  companion object {
    fun fromDeclaration(declaration: KSPropertyDeclaration): GeneratedProperty? {
      // For each contributed hint there are several properties, e.g. the reference itself
      // and the scopes. Group them by their common name without the suffix.
      val name = declaration.simpleName.asString()

      return when {
        name.endsWith(REFERENCE_SUFFIX) ->
          ReferenceProperty(declaration, name.substringBeforeLast(REFERENCE_SUFFIX))

        name.contains(SCOPE_SUFFIX) -> {
          // The old scope hint didn't have a number. Now that there can be multiple scopes
          // we append a number for all scopes, but we still need to support the old format.
          val indexString = name.substringAfterLast(SCOPE_SUFFIX)
          if (indexString.toIntOrNull() != null || indexString.isEmpty()) {
            ScopeProperty(declaration, name.substringBeforeLast(SCOPE_SUFFIX))
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
  val name: FqName,
  val moduleHash: Int
)
