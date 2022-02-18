package com.squareup.anvil.compiler.codegen

import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesBinding.Priority
import com.squareup.anvil.annotations.ContributesBinding.Priority.NORMAL
import com.squareup.anvil.compiler.contributesBindingFqName
import com.squareup.anvil.compiler.contributesMultibindingFqName
import com.squareup.anvil.compiler.internal.reference.AnnotationReference
import com.squareup.anvil.compiler.internal.reference.AnvilCompilationExceptionAnnotationReference
import com.squareup.anvil.compiler.internal.reference.ClassReference
import com.squareup.anvil.compiler.internal.reference.argumentAt

internal fun AnnotationReference.parentScope(): ClassReference {
  return argumentAt("parentScope", index = 1)
    ?.value()
    ?: throw AnvilCompilationExceptionAnnotationReference(
      message = "Couldn't find parentScope for $fqName.",
      annotationReference = this
    )
}

internal fun AnnotationReference.modules(): List<ClassReference> {
  return argumentAt("modules", 2)
    ?.value<List<ClassReference>>()
    .orEmpty()
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
    ?.value<String>()
    ?.let { ContributesBinding.Priority.valueOf(it) }
    ?: NORMAL
}
