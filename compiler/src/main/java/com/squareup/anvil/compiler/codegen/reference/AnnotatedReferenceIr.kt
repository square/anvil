package com.squareup.anvil.compiler.codegen.reference

import org.jetbrains.kotlin.name.FqName

internal interface AnnotatedReferenceIr {
  val annotations: List<AnnotationReferenceIr>

  fun isAnnotatedWith(fqName: FqName): Boolean =
    annotations.any { it.fqName == fqName }
}

internal fun List<AnnotationReferenceIr>.find(
  annotationName: FqName,
  scopeName: FqName? = null
): List<AnnotationReferenceIr> {
  return filter {
    it.fqName == annotationName && (scopeName == null || it.scopeOrNull?.fqName == scopeName)
  }
}

internal fun List<AnnotationReferenceIr>.findAll(
  vararg annotationNames: FqName,
  scopeName: FqName? = null
): List<AnnotationReferenceIr> {
  return filter {
    it.fqName in annotationNames && (scopeName == null || it.scopeOrNull?.fqName == scopeName)
  }
}
