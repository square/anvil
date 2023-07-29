package com.squareup.anvil.compiler.codegen.ksp

import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import kotlin.reflect.KClass

/**
 * Returns a sequence of [KSAnnotations][KSAnnotation] of the given [annotationKClass] type.
 */
internal fun KSAnnotated.getKSAnnotationsByType(
  annotationKClass: KClass<*>
): Sequence<KSAnnotation> {
  return annotations.filter {
    it.shortName.getShortName() == annotationKClass.simpleName &&
      it.annotationType.resolve()
      .declaration.qualifiedName?.asString() == annotationKClass.qualifiedName
  }
}
