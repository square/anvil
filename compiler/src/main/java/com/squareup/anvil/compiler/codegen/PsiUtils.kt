package com.squareup.anvil.compiler.codegen

import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile

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
  val annotationEntries = annotationEntries
  if (annotationEntries.isEmpty()) return false

  // Check first if the fully qualified name is used, e.g. `@dagger.Module`.
  val containsFullyQualifiedName = annotationEntries.any {
    it.text.startsWith("@${fqName.asString()}")
  }
  if (containsFullyQualifiedName) return true

  // Check if the simple name is used, e.g. `@Module`.
  val containsShortName = annotationEntries.any {
    it.shortName == fqName.shortName()
  }
  if (!containsShortName) return false

  // If the simple name is used, check that the annotation is imported.
  return containingKtFile.importDirectives
      .mapNotNull { it.importPath }
      .any {
        it.fqName == fqName
      }
}
