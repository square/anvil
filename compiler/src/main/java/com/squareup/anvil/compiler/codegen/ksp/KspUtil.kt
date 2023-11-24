package com.squareup.anvil.compiler.codegen.ksp

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.isAnnotationPresent
import com.google.devtools.ksp.symbol.ClassKind.ANNOTATION_CLASS
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSModifierListOwner
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.jvm.jvmSuppressWildcards
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

internal inline fun <reified T> KSAnnotated.isAnnotationPresent(): Boolean {
  return isAnnotationPresent(T::class)
}

internal fun KSAnnotated.isAnnotationPresent(klass: KClass<*>): Boolean {
  val fqcn = klass.qualifiedName ?: return false
  return getKSAnnotationsByQualifiedName(fqcn).firstOrNull() != null
}

internal fun KSClassDeclaration.isAnnotationClass(): Boolean = classKind == ANNOTATION_CLASS
internal fun KSModifierListOwner.isLateInit(): Boolean = Modifier.LATEINIT in modifiers



@OptIn(KspExperimental::class)
@ExperimentalAnvilApi
public fun TypeName.withJvmSuppressWildcardsIfNeeded(
  annotatedReference: KSAnnotated,
  type: KSType,
): TypeName {
  // If the parameter is annotated with @JvmSuppressWildcards, then add the annotation
  // to our type so that this information is forwarded when our Factory is compiled.
  val hasJvmSuppressWildcards = annotatedReference.isAnnotationPresent(JvmSuppressWildcards::class)

  // Add the @JvmSuppressWildcards annotation even for simple generic return types like
  // Set<String>. This avoids some edge cases where Dagger chokes.
  val isGenericType = (type.declaration as? KSClassDeclaration)?.typeParameters?.isNotEmpty() == true

  // Same for functions.
  val isFunctionType = type.isFunctionType

  return when {
    hasJvmSuppressWildcards || isGenericType -> this.jvmSuppressWildcards()
    isFunctionType -> this.jvmSuppressWildcards()
    else -> this
  }
}
