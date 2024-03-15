package com.squareup.anvil.compiler.codegen

import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.compiler.contributesBindingFqName
import com.squareup.anvil.compiler.contributesMultibindingFqName
import com.squareup.anvil.compiler.contributesSubcomponentFqName
import com.squareup.anvil.compiler.daggerComponentFqName
import com.squareup.anvil.compiler.daggerModuleFqName
import com.squareup.anvil.compiler.daggerSubcomponentFqName
import com.squareup.anvil.compiler.internal.reference.AnnotationReference
import com.squareup.anvil.compiler.internal.reference.AnnotationReference.Descriptor
import com.squareup.anvil.compiler.internal.reference.AnnotationReference.Psi
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
      annotationReference = this,
    )
}

internal fun AnnotationReference.ignoreQualifier(): Boolean {
  return argumentAt(
    name = "ignoreQualifier",
    index = when (fqName) {
      contributesBindingFqName -> 4
      contributesMultibindingFqName -> 3
      else -> return false
    },
  )
    ?.value<Boolean>() == true
}

internal fun ClassReference.qualifierAnnotation(): AnnotationReference? {
  return annotations.find { it.isQualifier() }
}

internal fun AnnotationReference.priority(): Int {
  return priorityNew() ?: priorityLegacy() ?: ContributesBinding.PRIORITY_NORMAL
}

@Suppress("DEPRECATION")
internal fun AnnotationReference.priorityLegacy(): Int? {
  val priority = argumentAt("priority", index = 4)
    ?.value<FqName>()
    ?.let { ContributesBinding.Priority.valueOf(it.shortName().asString()) }

  return priority?.value
}

internal fun AnnotationReference.priorityNew(): Int? {
  return argumentAt("priorityInt", index = 6)
    ?.value()
}

internal fun AnnotationReference.modules(
  parameterIndex: Int = modulesIndex(fqName),
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
      "Couldn't find index of modules argument for $annotationFqName.",
    )
  }
}

private fun modulesName(annotationFqName: FqName): String {
  return when (annotationFqName) {
    mergeComponentFqName, mergeSubcomponentFqName, contributesSubcomponentFqName -> "modules"
    mergeModulesFqName -> "includes"
    else -> throw NotImplementedError(
      "Couldn't find name of modules argument for $annotationFqName.",
    )
  }
}

internal fun <T : AnnotationReference> List<T>.find(
  annotationName: FqName,
  scope: ClassReference? = null,
): List<T> {
  return filter {
    it.fqName == annotationName && (scope == null || it.scopeOrNull() == scope)
  }
}

internal fun <T : AnnotationReference> List<T>.findAll(
  vararg annotationNames: FqName,
  scope: ClassReference? = null,
): List<T> {
  return filter {
    it.fqName in annotationNames && (scope == null || it.scopeOrNull() == scope)
  }
}

internal fun <T : AnnotationReference> List<T>.checkNoDuplicateScope(
  contributeAnnotation: Boolean,
) {
  // Exit early to avoid allocating additional collections.
  if (size < 2) return
  if (size == 2 && this[0].scope() != this[1].scope()) return

  // Check for duplicate scopes. Multiple contributions to the same scope are forbidden.
  val duplicates = groupBy { it.scope() }.filterValues { it.size > 1 }

  if (duplicates.isNotEmpty()) {
    val clazz = this[0].declaringClass()
    throw AnvilCompilationExceptionClassReference(
      classReference = clazz,
      message = if (contributeAnnotation) {
        "${clazz.fqName} contributes multiple times to the same scope: " +
          "${duplicates.keys.joinToString(prefix = "[", postfix = "]") { it.shortName }}. " +
          "Contributing multiple times to the same scope is forbidden and all scopes must " +
          "be distinct."
      } else {
        "${clazz.fqName} merges multiple times to the same scope: " +
          "${duplicates.keys.joinToString(prefix = "[", postfix = "]") { it.shortName }}. " +
          "Merging multiple times to the same scope is forbidden and all scopes must " +
          "be distinct."
      },
    )
  }
}

internal fun <T : AnnotationReference> List<T>.checkNoDuplicateScopeAndBoundType() {
  // Exit early to avoid allocating additional collections.
  if (size < 2) return
  if (size == 2 && this[0].scope() != this[1].scope()) return

  val duplicateScopes = groupBy { it.scope() }
    .filterValues { it.size > 1 }
    .ifEmpty { return }

  duplicateScopes.values.forEach { duplicateScopeAnnotations ->
    val duplicateBoundTypes = duplicateScopeAnnotations.groupBy { it.boundTypeOrNull() }
      .filterValues { it.size > 1 }
      .ifEmpty { return }
      .keys

    val clazz = this[0].declaringClass()
    throw AnvilCompilationExceptionClassReference(
      classReference = clazz,
      message = "${clazz.fqName} contributes multiple times to the same scope using the same " +
        "bound type: " +
        duplicateBoundTypes.joinToString(prefix = "[", postfix = "]") {
          it?.shortName ?: clazz.directSuperTypeReferences().single().asClassReference().shortName
        } +
        ". Contributing multiple times to the same scope with the same bound type is forbidden " +
        "and all scope - bound type combinations must be distinct.",
    )
  }
}

internal val AnnotationReference.daggerAnnotationFqName: FqName
  get() = when (fqName) {
    mergeComponentFqName -> daggerComponentFqName
    mergeSubcomponentFqName -> daggerSubcomponentFqName
    mergeModulesFqName -> daggerModuleFqName
    else -> throw NotImplementedError("Don't know how to handle $this.")
  }
