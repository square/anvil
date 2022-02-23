package com.squareup.anvil.compiler.codegen

import com.squareup.anvil.annotations.ContributesBinding.Priority
import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.internal.reference.AnnotationReference
import com.squareup.anvil.compiler.internal.reference.ClassReference
import com.squareup.anvil.compiler.internal.reference.ClassReference.Descriptor
import com.squareup.anvil.compiler.internal.reference.ClassReference.Psi
import com.squareup.anvil.compiler.internal.reference.allSuperTypeClassReferences
import com.squareup.anvil.compiler.internal.reference.asClassName
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.TypeName
import org.jetbrains.kotlin.types.KotlinType
import kotlin.LazyThreadSafetyMode.NONE

internal data class ContributedBinding(
  val contributedClass: ClassReference,
  val mapKeys: List<AnnotationSpec>,
  val qualifiers: List<AnnotationSpec>,
  val boundTypeClassName: TypeName,
  val priority: Priority,
  val qualifiersKeyLazy: Lazy<String>
)

internal fun AnnotationReference.toContributedBinding(
  isMultibinding: Boolean
): ContributedBinding {
  val boundType = requireBoundType()

  val mapKeys = if (isMultibinding) {
    declaringClass().annotations.filter { it.isMapKey() }.map { it.toAnnotationSpec() }
  } else {
    emptyList()
  }

  val ignoreQualifier = ignoreQualifier()
  val qualifiers = if (ignoreQualifier) {
    emptyList()
  } else {
    declaringClass().annotations.filter { it.isQualifier() }.map { it.toAnnotationSpec() }
  }

  return ContributedBinding(
    contributedClass = declaringClass(),
    mapKeys = mapKeys,
    qualifiers = qualifiers,
    boundTypeClassName = boundType.asClassName(),
    priority = priority(),
    qualifiersKeyLazy = declaringClass().qualifiersKeyLazy(boundType, ignoreQualifier)
  )
}

private fun AnnotationReference.requireBoundType(): ClassReference {
  val boundFromAnnotation = boundTypeOrNull()

  if (boundFromAnnotation != null) {
    // ensure that the bound type is actually a supertype of the contributing class
    val boundType = declaringClass().allSuperTypeClassReferences()
      .firstOrNull { it.fqName == boundFromAnnotation.fqName }
      ?: throw AnvilCompilationException(
        "$fqName contributes a binding for ${boundFromAnnotation.fqName}, " +
          "but doesn't extend this type."
      )

    boundType.checkNotGeneric(contributedClass = declaringClass())
    return boundType
  }

  // If there's no bound type in the annotation,
  // it must be the only supertype of the contributing class
  val boundType = declaringClass().directSuperClassReferences().singleOrNull()
    ?: throw AnvilCompilationException(
      message = "$fqName contributes a binding, but does not " +
        "specify the bound type. This is only allowed with exactly one direct super type. " +
        "If there are multiple or none, then the bound type must be explicitly defined in " +
        "the @$shortName annotation."
    )

  boundType.checkNotGeneric(contributedClass = declaringClass())
  return boundType
}

private fun ClassReference.checkNotGeneric(
  contributedClass: ClassReference
) {
  fun exceptionText(typeString: String): String {
    return "Class ${contributedClass.fqName} binds $fqName," +
      " but the bound type contains type parameter(s) $typeString." +
      " Type parameters in bindings are not supported. This binding needs" +
      " to be contributed in a Dagger module manually."
  }

  fun KotlinType.describeTypeParameters(): String = arguments
    .ifEmpty { return "" }
    .joinToString(prefix = "<", postfix = ">") { typeArgument ->
      typeArgument.type.toString() + typeArgument.type.describeTypeParameters()
    }

  when (this) {
    is Descriptor -> {
      if (clazz.declaredTypeParameters.isNotEmpty()) {

        throw AnvilCompilationException(
          classDescriptor = clazz,
          message = exceptionText(clazz.defaultType.describeTypeParameters())
        )
      }
    }
    is Psi -> {
      if (clazz.typeParameters.isNotEmpty()) {
        val typeString = clazz.typeParameters
          .joinToString(prefix = "<", postfix = ">") { it.name!! }

        throw AnvilCompilationException(
          message = exceptionText(typeString),
          element = clazz.nameIdentifier
        )
      }
    }
  }
}

private fun ClassReference.qualifiersKeyLazy(
  boundType: ClassReference,
  ignoreQualifier: Boolean
): Lazy<String> {
  // Careful! If we ever decide to support generic types, then we might need to use the
  // Kotlin type and not just the FqName.
  if (ignoreQualifier) {
    return lazy { boundType.fqName.asString() }
  }

  return lazy(NONE) { boundType.fqName.asString() + qualifiersKey() }
}

private fun ClassReference.qualifiersKey(): String {
  return annotations
    .filter { it.isQualifier() }
    // Note that we sort all elements. That's important for a stable string comparison.
    .sortedBy { it.classReference }
    .joinToString(separator = "") { annotation ->
      annotation.fqName.asString() +
        annotation.arguments.joinToString(separator = "") { argument ->
          val valueString = when (val value = argument.value<Any>()) {
            is ClassReference -> value.fqName.asString()
            else -> value.toString()
          }

          argument.resolvedName + valueString
        }
    }
}
