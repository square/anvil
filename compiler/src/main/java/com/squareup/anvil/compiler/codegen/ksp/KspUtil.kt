package com.squareup.anvil.compiler.codegen.ksp

import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import kotlin.reflect.KClass

/**
 * Returns a sequence of [KSAnnotations][KSAnnotation] of the given [annotationKClass] type.
 */
internal fun <T : Annotation> KSAnnotated.getKSAnnotationsByType(
  annotationKClass: KClass<T>,
): Sequence<KSAnnotation> {
  return annotations.filter {
    it.shortName.getShortName() == annotationKClass.simpleName &&
      it.annotationType.resolve()
        .declaration.qualifiedName?.asString() == annotationKClass.qualifiedName
  }
}

/**
 * Returns a sequence of [KSAnnotations][KSAnnotation] of the given [qualifiedName].
 */
internal fun KSAnnotated.getKSAnnotationsByQualifiedName(
  qualifiedName: String,
): Sequence<KSAnnotation> {
  val simpleName = qualifiedName.substringAfterLast(".")
  return annotations.filter {
    it.shortName.getShortName() == simpleName &&
      it.annotationType.resolve()
        .declaration.qualifiedName?.asString() == qualifiedName
  }
}

internal fun KSAnnotated.isAnnotationPresent(qualifiedName: String): Boolean =
  getKSAnnotationsByQualifiedName(qualifiedName).firstOrNull() != null
