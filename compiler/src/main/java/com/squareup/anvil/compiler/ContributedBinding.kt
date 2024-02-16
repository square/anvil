package com.squareup.anvil.compiler

import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSType
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.compiler.codegen.ksp.resolveKSClassDeclaration
import com.squareup.anvil.compiler.codegen.reference.AnnotationReferenceIr
import com.squareup.anvil.compiler.codegen.reference.AnvilCompilationExceptionClassReferenceIr
import com.squareup.anvil.compiler.codegen.reference.ClassReferenceIr
import com.squareup.anvil.compiler.internal.reference.AnnotationReference
import com.squareup.anvil.compiler.internal.reference.ClassReference

internal data class ContributedBinding(
  val scope: ClassReferenceIr,
  val isMultibinding: Boolean,
  val declaringClass: ClassReferenceIr,
  val implClass: ClassReferenceIr,
  val boundType: ClassReferenceIr,
  val qualifierKey: String,
  val priority: ContributesBinding.Priority,
)

internal fun List<ContributedBinding>.findHighestPriorityBinding(): ContributedBinding {
  if (size == 1) return this[0]

  val bindings = groupBy { it.priority }
    .toSortedMap()
    .let { it.getValue(it.lastKey()) }
    // In some very rare cases we can see a binding for the same type twice. Just in case filter
    // them, see https://github.com/square/anvil/issues/460.
    .distinctBy { it.boundType }

  if (bindings.size > 1) {
    throw AnvilCompilationExceptionClassReferenceIr(
      bindings[0].boundType,
      "There are multiple contributed bindings with the same bound type. The bound type is " +
        "${bindings[0].boundType.fqName.asString()}. The contributed binding classes are: " +
        bindings.joinToString(
          prefix = "[",
          postfix = "]",
        ) { it.implClass.fqName.asString() },
    )
  }

  return bindings[0]
}

internal fun AnnotationReference.qualifierKey(): String {
  return fqName.asString() +
    arguments.joinToString(separator = "") { argument ->
      val valueString = when (val value = argument.value<Any>()) {
        is ClassReference -> value.fqName.asString()
        // TODO what if it's another annotation?
        else -> value.toString()
      }

      argument.resolvedName + valueString
    }
}

internal fun KSAnnotation.qualifierKey(): String {
  return shortName.asString() +
    arguments.joinToString(separator = "") { argument ->
      val valueString = when (val value = argument.value) {
        is KSType -> value.resolveKSClassDeclaration()!!.qualifiedName!!.asString()
        // TODO what if it's another annotation?
        else -> value.toString()
      }

      argument.name!!.asString() + valueString
    }
}
