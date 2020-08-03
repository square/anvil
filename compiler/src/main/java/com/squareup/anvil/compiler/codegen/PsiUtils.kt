package com.squareup.anvil.compiler.codegen

import com.squareup.anvil.compiler.AnvilCompilationException
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.resolveClassByFqName
import org.jetbrains.kotlin.incremental.components.NoLookupLocation.FROM_BACKEND
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassLiteralExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentName
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe

internal fun KtFile.classesAndInnerClasses(): Sequence<KtClassOrObject> {
  val children = findChildrenByClass(KtClassOrObject::class.java)

  return generateSequence(children.toList()) { list ->
    list
        .flatMap {
          it.declarations.filterIsInstance<KtClassOrObject>()
        }
        .ifEmpty { null }
  }.flatMap { it.asSequence() }
}

internal fun KtClassOrObject.requireFqName(): FqName = requireNotNull(fqName) {
  "fqName was null for $this, $nameAsSafeName"
}

internal fun KtClassOrObject.isInterface(): Boolean = this is KtClass && this.isInterface()

internal fun KtClassOrObject.hasAnnotation(fqName: FqName): Boolean {
  return findAnnotation(fqName) != null
}

internal fun KtClassOrObject.findAnnotation(fqName: FqName): KtAnnotationEntry? {
  val annotationEntries = annotationEntries
  if (annotationEntries.isEmpty()) return null

  // Check first if the fully qualified name is used, e.g. `@dagger.Module`.
  val annotationEntry = annotationEntries.firstOrNull {
    it.text.startsWith("@${fqName.asString()}")
  }
  if (annotationEntry != null) return annotationEntry

  // Check if the simple name is used, e.g. `@Module`.
  val annotationEntryShort = annotationEntries
      .firstOrNull {
        it.shortName == fqName.shortName()
      }
      ?: return null

  // If the simple name is used, check that the annotation is imported.
  val hasImport = containingKtFile.importDirectives
      .mapNotNull { it.importPath }
      .any {
        it.fqName == fqName
      }

  return if (hasImport) annotationEntryShort else null
}

internal fun KtClassOrObject.scope(
  annotationFqName: FqName,
  module: ModuleDescriptor
): FqName {
  val scopeElement = findScopeClassLiteralExpression(annotationFqName)
      .let {
        val children = it.children
        children.singleOrNull() ?: throw AnvilCompilationException(
            "Expected a single child, but had ${children.size} instead: ${it.text}",
            element = this
        )
      }

  val scopeClassReference = when (scopeElement) {
    // If a fully qualified name is used, then we're done and don't need to do anything further.
    is KtDotQualifiedExpression -> return FqName(scopeElement.text)
    is KtNameReferenceExpression -> scopeElement.getReferencedName()
    else -> throw AnvilCompilationException(
        "Don't know how to handle Psi element: ${scopeElement.text}",
        element = this
    )
  }

  // First look in the imports for the reference name. If the class is imported, then we know the
  // fully qualified name.
  containingKtFile.importDirectives
      .mapNotNull { it.importPath }
      .firstOrNull {
        it.fqName.shortName()
            .asString() == scopeClassReference
      }
      ?.let { return it.fqName }

  // If there is no import, then try to resolve the class with the same package as this file.
  module
      .resolveClassByFqName(
          FqName("${containingKtFile.packageFqName}.$scopeClassReference"),
          FROM_BACKEND
      )
      ?.let { return it.fqNameSafe }

  // If this doesn't work, then maybe a class from the Kotlin package is used.
  module.resolveClassByFqName(FqName("kotlin.$scopeClassReference"), FROM_BACKEND)
      ?.let { return it.fqNameSafe }

  // Everything else isn't supported.
  throw AnvilCompilationException(
      "Couldn't resolve scope $scopeClassReference for Psi element: $text",
      element = this
  )
}

private fun KtClassOrObject.findScopeClassLiteralExpression(
  annotationFqName: FqName
): KtClassLiteralExpression {
  val annotationEntry = findAnnotation(annotationFqName)
      ?: throw AnvilCompilationException(
          "Couldn't find $annotationFqName for Psi element: $text",
          element = this
      )

  val annotationValues = annotationEntry.valueArguments
      .asSequence()
      .filterIsInstance<KtValueArgument>()

  // First check if the is any named parameter. Named parameters allow a different order of
  // arguments.
  annotationValues
      .mapNotNull { valueArgument ->
        val children = valueArgument.children
        if (children.size == 2 && children[0] is KtValueArgumentName &&
            (children[0] as KtValueArgumentName).asName.asString() == "scope" &&
            children[1] is KtClassLiteralExpression
        ) {
          children[1] as KtClassLiteralExpression
        } else {
          null
        }
      }
      .firstOrNull()
      ?.let { return it }

  // If there is no named argument, then take the first argument, which must be a class literal
  // expression, e.g. @ContributesTo(Unit::class)
  return annotationValues
      .firstOrNull()
      ?.let { valueArgument ->
        valueArgument.children.firstOrNull() as? KtClassLiteralExpression
      }
      ?: throw AnvilCompilationException(
          "The first argument for $annotationFqName must be a class literal: $text",
          element = this
      )
}
