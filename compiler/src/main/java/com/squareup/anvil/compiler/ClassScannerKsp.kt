package com.squareup.anvil.compiler

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.anvil.compiler.ClassScannerKsp.GeneratedProperty.ReferenceProperty
import com.squareup.anvil.compiler.ClassScannerKsp.GeneratedProperty.ScopeProperty
import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.codegen.ksp.resolveKSClassDeclaration
import com.squareup.anvil.compiler.codegen.ksp.scope
import com.squareup.kotlinpoet.ksp.toClassName
import org.jetbrains.kotlin.name.FqName

internal class ClassScannerKsp {

  private val cache = mutableMapOf<CacheKey, Collection<List<GeneratedProperty>>>()

  /**
   * Returns a sequence of contributed classes from the dependency graph. Note that the result
   * includes inner classes already.
   */
  @OptIn(KspExperimental::class)
  fun findContributedClasses(
    resolver: Resolver,
    annotation: FqName,
    scope: KSType?,
  ): Sequence<KSClassDeclaration> {
    val propertyGroups: Collection<List<GeneratedProperty>> =
      cache.getOrPut(CacheKey(annotation, resolver.hashCode())) {
        resolver.getDeclarationsFromPackage(HINT_PACKAGE)
          .filterIsInstance<KSPropertyDeclaration>()
          .mapNotNull { GeneratedProperty.from(it) }
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
          .map { it.declaration.type.resolve() }

        // Look for the right scope even before resolving the class and resolving all its super
        // types.
        if (scope != null && scope !in scopes) return@mapNotNull null

        reference.declaration.type
          .resolve().resolveKSClassDeclaration()
      }
      .filter { clazz ->
        // Check that the annotation really is present. It should always be the case, but it's
        // a safetynet in case the generated properties are out of sync.
        clazz.annotations.any {
          it.annotationType.resolve().toClassName().fqName == annotation && (scope == null || it.scope() == scope)
        }
      }
  }

  private sealed class GeneratedProperty(
    val declaration: KSPropertyDeclaration,
    val baseName: String,
  ) {
    class ReferenceProperty(
      declaration: KSPropertyDeclaration,
      baseName: String,
    ) : GeneratedProperty(declaration, baseName)

    class ScopeProperty(
      declaration: KSPropertyDeclaration,
      baseName: String,
    ) : GeneratedProperty(declaration, baseName)

    companion object {
      fun from(declaration: KSPropertyDeclaration): GeneratedProperty? {
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
    val fqName: FqName,
    val resolverHash: Int,
  )
}
