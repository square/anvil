package com.squareup.anvil.compiler.ksp

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.isAnnotationPresent
import com.google.devtools.ksp.isLocal
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSName
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.toKSName
import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.anvil.compiler.ANVIL_MODULE_SUFFIX
import com.squareup.anvil.compiler.MODULE_PACKAGE_PREFIX
import com.squareup.anvil.compiler.codegen.ksp.KspAnvilException
import com.squareup.anvil.compiler.codegen.ksp.argumentAt
import com.squareup.anvil.compiler.codegen.ksp.getKSAnnotationsByQualifiedName
import com.squareup.anvil.compiler.codegen.ksp.getKSAnnotationsByType
import com.squareup.anvil.compiler.codegen.ksp.scope
import com.squareup.anvil.compiler.codegen.ksp.scopeOrNull
import com.squareup.anvil.compiler.internal.classIdBestGuess
import com.squareup.anvil.compiler.internal.reference.argumentAt
import com.squareup.anvil.compiler.internal.reference.asClassId
import com.squareup.anvil.compiler.internal.reference.generateClassName
import com.squareup.anvil.compiler.internal.safePackageString
import com.squareup.kotlinpoet.ksp.toClassName
import dagger.MapKey
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import javax.inject.Qualifier
import javax.inject.Scope
import kotlin.reflect.KClass

val KSClassDeclaration.fqName: FqName get() {
  return qualifiedName?.let {
    FqName(it.asString())
  } ?: throw KspAnvilException(
    message = "Couldn't find qualified name for '$this'.",
    node = this,
  )
}

val KSClassDeclaration.classId: ClassId get() {
  return fqName.classIdBestGuess()
}

internal fun ClassId.classDeclarationOrNull(
  resolver: Resolver,
): KSClassDeclaration? = resolver.getClassDeclarationByName(toKSName())

val KSTypeReference.singleArgumentType: KSType get() {
  return resolve().arguments.single().type?.resolve() ?: throw KspAnvilException(
    message = "Expected a single type argument, but found none.",
    node = this,
  )
}

val KSType.classDeclaration: KSClassDeclaration get() {
  return declaration as? KSClassDeclaration ?: throw KspAnvilException(
    message = "Expected declaration to be a class.",
    node = declaration,
  )
}

internal fun KSAnnotation.declaringClass(): KSClassDeclaration {
  return parent as? KSClassDeclaration ?: throw KspAnvilException(
    message = "Expected declaration to be a class.",
    node = this,
  )
}

internal fun KSAnnotated.atLeastOneAnnotation(
  annotationClass: String,
  scope: KSType? = null,
): Sequence<KSAnnotation> {
  return findAllKSAnnotations(annotationClass)
    .filter { it.scopeOrNull() == scope }
    .ifEmpty {
      throw KspAnvilException(
        message = "Class $this is not annotated with $annotationClass" +
          "${if (scope == null) "" else " with scope ${scope.classDeclaration.fqName}"}.",
        node = this,
      )
    }
}

internal fun KSAnnotated.atLeastOneAnnotation(
  annotationClass: KClass<out Annotation>,
  scope: KSType? = null,
): Sequence<KSAnnotation> {
  return findAllKSAnnotations(annotationClass)
    .filter { it.scopeOrNull() == scope }
    .ifEmpty {
      throw KspAnvilException(
        message = "Class $this is not annotated with $annotationClass" +
          "${if (scope == null) "" else " with scope ${scope.classDeclaration.fqName}"}.",
        node = this,
      )
    }
}

// If we're evaluating an anonymous inner class, it cannot merge anything and will cause
// a failure if we try to resolve its [ClassId]
internal fun KSDeclaration.shouldIgnore(): Boolean {
  return qualifiedName == null || isLocal()
}

internal fun createAnvilModuleName(clazz: KSClassDeclaration): String {
  return "$MODULE_PACKAGE_PREFIX." +
    clazz.packageName.safePackageString() +
    clazz.generateClassName(
      separator = "",
      suffix = ANVIL_MODULE_SUFFIX,
    ).relativeClassName.toString()
}

@ExperimentalAnvilApi
internal fun KSClassDeclaration.generateClassName(
  separator: String = "_",
  suffix: String = "",
): ClassId {
  return toClassName().generateClassName(separator, suffix).asClassId()
}

internal fun KSName.safePackageString(
  dotPrefix: Boolean = false,
  dotSuffix: Boolean = true,
): String = toString().safePackageString(isRoot, dotPrefix, dotSuffix)

private val KSName.isRoot: Boolean get() = asString().isEmpty()

fun KSAnnotated.findAllKSAnnotations(vararg annotations: KClass<out Annotation>): Sequence<KSAnnotation> {
  return sequence {
    for (annotation in annotations) {
      yieldAll(getKSAnnotationsByType(annotation))
    }
  }
}
fun KSAnnotated.findAllKSAnnotations(vararg annotations: String): Sequence<KSAnnotation> {
  return sequence {
    for (annotation in annotations) {
      yieldAll(getKSAnnotationsByQualifiedName(annotation))
    }
  }
}

internal fun KSAnnotation.replaces(): List<KSType> =
  argumentAt("replaces")?.value as? List<KSType> ?: emptyList()

internal fun KSAnnotation.exclude(): List<KSType> =
  argumentAt("exclude")?.value as? List<KSType> ?: emptyList()

internal fun KSAnnotation.parentScope(): KSClassDeclaration {
  return argumentAt("parentScope")
    ?.value as? KSClassDeclaration
    ?: throw KspAnvilException(
      message = "Couldn't find parentScope for $this.",
      node = this,
    )
}

@OptIn(KspExperimental::class)
internal fun KSAnnotated.isQualifier(): Boolean = isAnnotationPresent(Qualifier::class)

@OptIn(KspExperimental::class)
internal fun KSAnnotated.isMapKey(): Boolean = isAnnotationPresent(MapKey::class)

@OptIn(KspExperimental::class)
internal fun KSAnnotated.isDaggerScope(): Boolean = isAnnotationPresent(Scope::class)
