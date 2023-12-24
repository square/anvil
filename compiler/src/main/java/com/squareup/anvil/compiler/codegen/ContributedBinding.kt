package com.squareup.anvil.compiler.codegen

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.anvil.annotations.ContributesBinding.Priority
import com.squareup.anvil.compiler.anyFqName
import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.codegen.ksp.KspAnvilException
import com.squareup.anvil.compiler.codegen.ksp.boundTypeOrNull
import com.squareup.anvil.compiler.codegen.ksp.declaringClass
import com.squareup.anvil.compiler.codegen.ksp.ignoreQualifier
import com.squareup.anvil.compiler.codegen.ksp.isMapKey
import com.squareup.anvil.compiler.codegen.ksp.isQualifier
import com.squareup.anvil.compiler.codegen.ksp.priority
import com.squareup.anvil.compiler.codegen.ksp.resolveKSClassDeclaration
import com.squareup.anvil.compiler.codegen.ksp.superTypesExcludingAny
import com.squareup.anvil.compiler.internal.reference.AnnotationReference
import com.squareup.anvil.compiler.internal.reference.ClassReference
import com.squareup.anvil.compiler.internal.reference.ClassReference.Descriptor
import com.squareup.anvil.compiler.internal.reference.ClassReference.Psi
import com.squareup.anvil.compiler.internal.reference.allSuperTypeClassReferences
import com.squareup.anvil.compiler.internal.reference.asClassName
import com.squareup.anvil.compiler.internal.reference.toClassReference
import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ksp.toAnnotationSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.toTypeVariableName
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.types.KotlinType
import kotlin.LazyThreadSafetyMode.NONE

internal data class ContributedBinding(
  val isMultibinding: Boolean,
  val contributedClass: ClassName,
  val mapKeys: List<AnnotationSpec>,
  val qualifiers: List<AnnotationSpec>,
  val boundType: ClassName,
  val priority: Priority,
  val qualifiersKeyLazy: Lazy<String>,
  val contributedClassIsObject: Boolean,
)

internal fun AnnotationReference.toContributedBinding(
  isMultibinding: Boolean,
  module: ModuleDescriptor,
): ContributedBinding {
  val boundType = requireBoundType(module)

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

  val declaringClass = declaringClass()
  return ContributedBinding(
    isMultibinding = isMultibinding,
    contributedClass = declaringClass.asClassName(),
    mapKeys = mapKeys,
    qualifiers = qualifiers,
    boundType = boundType.asClassName(),
    priority = priority(),
    qualifiersKeyLazy = declaringClass.qualifiersKeyLazy(boundType, ignoreQualifier),
    contributedClassIsObject = declaringClass.isObject(),
  )
}

private fun AnnotationReference.requireBoundType(module: ModuleDescriptor): ClassReference {
  val boundFromAnnotation = boundTypeOrNull()

  if (boundFromAnnotation != null) {
    // Since all classes extend Any, we can stop here.
    if (boundFromAnnotation.fqName == anyFqName) return anyFqName.toClassReference(module)

    // ensure that the bound type is actually a supertype of the contributing class
    val boundType = declaringClass().allSuperTypeClassReferences()
      .firstOrNull { it.fqName == boundFromAnnotation.fqName }
      ?: throw AnvilCompilationException(
        "$fqName contributes a binding for ${boundFromAnnotation.fqName}, " +
          "but doesn't extend this type.",
      )

    boundType.checkNotGeneric(contributedClass = declaringClass())
    return boundType
  }

  // If there's no bound type in the annotation,
  // it must be the only supertype of the contributing class
  val boundType = declaringClass().directSuperTypeReferences().singleOrNull()
    ?.asClassReference()
    ?: throw AnvilCompilationException(
      message = "$fqName contributes a binding, but does not " +
        "specify the bound type. This is only allowed with exactly one direct super type. " +
        "If there are multiple or none, then the bound type must be explicitly defined in " +
        "the @$shortName annotation.",
    )

  boundType.checkNotGeneric(contributedClass = declaringClass())
  return boundType
}

private fun ClassReference.checkNotGeneric(
  contributedClass: ClassReference,
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
          message = exceptionText(clazz.defaultType.describeTypeParameters()),
        )
      }
    }
    is Psi -> {
      if (clazz.typeParameters.isNotEmpty()) {
        val typeString = clazz.typeParameters
          .joinToString(prefix = "<", postfix = ">") { it.name!! }

        throw AnvilCompilationException(
          message = exceptionText(typeString),
          element = clazz.nameIdentifier,
        )
      }
    }
  }
}

private fun ClassReference.qualifiersKeyLazy(
  boundType: ClassReference,
  ignoreQualifier: Boolean,
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

internal fun KSAnnotation.toContributedBinding(
  isMultibinding: Boolean,
  resolver: Resolver,
): ContributedBinding {
  val boundType = requireBoundType(resolver)

  val mapKeys = if (isMultibinding) {
    declaringClass().annotations.filter { it.isMapKey() }.map { it.toAnnotationSpec() }
      .toList()
  } else {
    emptyList()
  }

  val ignoreQualifier = ignoreQualifier()
  val qualifiers = if (ignoreQualifier) {
    emptyList()
  } else {
    declaringClass().annotations.filter { it.isQualifier() }.map { it.toAnnotationSpec() }
      .toList()
  }

  val declaringClass = declaringClass()
  return ContributedBinding(
    isMultibinding = isMultibinding,
    contributedClass = declaringClass.toClassName(),
    mapKeys = mapKeys,
    qualifiers = qualifiers,
    boundType = boundType.toClassName(),
    priority = priority(),
    qualifiersKeyLazy = declaringClass.qualifiersKeyLazy(boundType, ignoreQualifier),
    contributedClassIsObject = declaringClass.classKind == ClassKind.OBJECT,
  )
}

private fun KSAnnotation.requireBoundType(resolver: Resolver): KSClassDeclaration {
  val boundFromAnnotation = boundTypeOrNull()?.toClassName()

  val declaringClass = declaringClass()
  if (boundFromAnnotation != null) {
    // Since all classes extend Any, we can stop here.
    if (boundFromAnnotation == ANY) return resolver.builtIns.anyType.resolveKSClassDeclaration()!!

    // ensure that the bound type is actually a supertype of the contributing class
    val boundType = declaringClass.superTypesExcludingAny(resolver)
      .firstOrNull { it.toTypeName() == boundFromAnnotation }
      ?.resolve()
      ?.resolveKSClassDeclaration()
      ?: throw KspAnvilException(
        "${declaringClass.qualifiedName?.asString()} contributes a binding for ${boundFromAnnotation}, " +
          "but doesn't extend this type.",
        node = this,
      )

    boundType.checkNotGeneric(contributedClass = declaringClass)
    return boundType
  }

  // If there's no bound type in the annotation,
  // it must be the only supertype of the contributing class
  val boundType = declaringClass().superTypes.singleOrNull()
    ?.resolve()
    ?.resolveKSClassDeclaration()
    ?: throw KspAnvilException(
      message = "${declaringClass.qualifiedName?.asString()} contributes a binding, but does not " +
        "specify the bound type. This is only allowed with exactly one direct super type. " +
        "If there are multiple or none, then the bound type must be explicitly defined in " +
        "the @$shortName annotation.",
      node = this,
    )

  boundType.checkNotGeneric(contributedClass = declaringClass())
  return boundType
}

private fun KSClassDeclaration.checkNotGeneric(
  contributedClass: KSClassDeclaration,
) {
  fun exceptionText(typeString: String): String {
    return "Class ${contributedClass.qualifiedName?.asString()} binds ${qualifiedName?.asString()}," +
      " but the bound type contains type parameter(s) $typeString." +
      " Type parameters in bindings are not supported. This binding needs" +
      " to be contributed in a Dagger module manually."
  }

  fun KSClassDeclaration.describeTypeParameters(): String = typeParameters
    .ifEmpty { return "" }
    .joinToString(prefix = "<", postfix = ">") { typeArgument ->
      typeArgument.toTypeVariableName().toString()
    }

  if (typeParameters.isNotEmpty()) {
    throw KspAnvilException(
      node = this,
      message = exceptionText(describeTypeParameters()),
    )
  }
}

private fun KSClassDeclaration.qualifiersKeyLazy(
  boundType: KSClassDeclaration,
  ignoreQualifier: Boolean,
): Lazy<String> {
  // Careful! If we ever decide to support generic types, then we might need to use the
  // Kotlin type and not just the FqName.
  if (ignoreQualifier) {
    return lazy { boundType.qualifiedName!!.asString() }
  }

  return lazy(NONE) { boundType.qualifiedName!!.asString() + qualifiersKey() }
}

private fun KSClassDeclaration.qualifiersKey(): String {
  return annotations
    .filter { it.isQualifier() }
    .associateBy { it.annotationType.resolve().declaration.qualifiedName!!.asString() }
    // Note that we sort all elements. That's important for a stable string comparison.
    .toSortedMap(compareBy { it })
    .entries
    .joinToString(separator = "") { (fqName, annotation) ->
      fqName +
        annotation.arguments.joinToString(separator = "") { argument ->
          val valueString = when (val value = argument.value) {
            is KSType -> value.resolveKSClassDeclaration()!!.qualifiedName!!.asString()
            else -> value.toString()
          }

          argument.name!!.asString() + valueString
        }
    }
}
