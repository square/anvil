package com.squareup.anvil.compiler.codegen

import com.squareup.anvil.annotations.ContributesBinding.Priority
import com.squareup.anvil.annotations.ContributesBinding.Priority.NORMAL
import com.squareup.anvil.compiler.contributesBindingFqName
import com.squareup.anvil.compiler.contributesMultibindingFqName
import com.squareup.anvil.compiler.contributesSubcomponentFqName
import com.squareup.anvil.compiler.internal.reference.AnnotationReference
import com.squareup.anvil.compiler.internal.reference.AnvilCompilationExceptionAnnotationReference
import com.squareup.anvil.compiler.internal.reference.AnvilCompilationExceptionClassReference
import com.squareup.anvil.compiler.internal.reference.ClassReference
import com.squareup.anvil.compiler.internal.reference.argumentAt
import com.squareup.anvil.compiler.mergeComponentFqName
import com.squareup.anvil.compiler.mergeModulesFqName
import com.squareup.anvil.compiler.mergeSubcomponentFqName
import org.jetbrains.kotlin.name.FqName

internal fun AnnotationReference.parentScope(): ClassReference {
  return argumentAt("parentScope", index = 1)
    ?.value()
    ?: throw AnvilCompilationExceptionAnnotationReference(
      message = "Couldn't find parentScope for $fqName.",
      annotationReference = this
    )
}

internal fun AnnotationReference.ignoreQualifier(): Boolean {
  return argumentAt(
    name = "ignoreQualifier",
    index = when (fqName) {
      contributesBindingFqName -> 4
      contributesMultibindingFqName -> 3
      else -> return false
    }
  )
    ?.value()
    ?: false
}

internal fun AnnotationReference.priority(): Priority {
  return argumentAt("priority", index = 4)
    ?.value<FqName>()
    ?.let { Priority.valueOf(it.shortName().asString()) }
    ?: NORMAL
}

internal fun AnnotationReference.modules(
  parameterIndex: Int = modulesIndex(fqName)
): List<ClassReference> {
  return argumentAt(modulesName(fqName), parameterIndex)
    ?.value<List<ClassReference>>()
    .orEmpty()
}

private fun modulesIndex(annotationFqName: FqName): Int {
  return when (annotationFqName) {
    mergeComponentFqName, mergeSubcomponentFqName, mergeModulesFqName -> 1
    contributesSubcomponentFqName -> 2
    else -> throw NotImplementedError(
      "Couldn't find index of modules argument for $annotationFqName."
    )
  }
}

private fun modulesName(annotationFqName: FqName): String {
  return when (annotationFqName) {
    mergeComponentFqName, mergeSubcomponentFqName, contributesSubcomponentFqName -> "modules"
    mergeModulesFqName -> "includes"
    else -> throw NotImplementedError(
      "Couldn't find name of modules argument for $annotationFqName."
    )
  }
}

internal fun <T : AnnotationReference> List<T>.find(
  annotationName: FqName,
  scopeName: FqName? = null
): List<T> {
  return filter {
    it.fqName == annotationName &&
      (scopeName == null || it.scopeOrNull()?.fqName == scopeName)
  }
}

internal fun <T : AnnotationReference> List<T>.checkNoDuplicateScope() {
  // Exit early to avoid allocating additional collections.
  if (size < 2) return
  if (size == 2 && this[0].scope() != this[1].scope()) return

  // Check for duplicate scopes. Multiple contributions to the same scope are forbidden.
  val duplicates = groupBy { it.scope() }.filterValues { it.size > 1 }

  if (duplicates.isNotEmpty()) {
    val clazz = this[0].declaringClass()
    throw AnvilCompilationExceptionClassReference(
      classReference = clazz,
      message = "${clazz.fqName} contributes multiple times to the same scope: " +
        "${duplicates.keys.joinToString(prefix = "[", postfix = "]") { it.shortName }}. " +
        "Contributing multiple times to the same scope is forbidden and all scopes must " +
        "be distinct.",
    )
  }
}
