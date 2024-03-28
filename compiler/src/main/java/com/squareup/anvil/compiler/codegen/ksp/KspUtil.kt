package com.squareup.anvil.compiler.codegen.ksp

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.isAnnotationPresent
import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind.ANNOTATION_CLASS
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSModifierListOwner
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.jvm.jvmSuppressWildcards
import dagger.assisted.AssistedInject
import javax.inject.Inject
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
internal fun TypeName.withJvmSuppressWildcardsIfNeeded(
  annotatedReference: KSAnnotated,
  type: KSType,
): TypeName {
  // If the parameter is annotated with @JvmSuppressWildcards, then add the annotation
  // to our type so that this information is forwarded when our Factory is compiled.
  val hasJvmSuppressWildcards = annotatedReference.isAnnotationPresent(JvmSuppressWildcards::class)

  // Add the @JvmSuppressWildcards annotation even for simple generic return types like
  // Set<String>. This avoids some edge cases where Dagger chokes.
  val isGenericType =
    (type.declaration as? KSClassDeclaration)?.typeParameters?.isNotEmpty() == true

  // Same for functions.
  val isFunctionType = type.isFunctionType

  return when {
    hasJvmSuppressWildcards || isGenericType -> this.jvmSuppressWildcards()
    isFunctionType -> this.jvmSuppressWildcards()
    else -> this
  }
}

/**
 * Resolves the [KSClassDeclaration] for this type, including following typealiases as needed.
 */
internal tailrec fun KSType.resolveKSClassDeclaration(): KSClassDeclaration? {
  return when (val declaration = declaration) {
    is KSClassDeclaration -> declaration
    is KSTypeAlias -> declaration.type.resolve().resolveKSClassDeclaration()
    else -> error("Unrecognized declaration type: $declaration")
  }
}

/**
 * Returns a sequence of all `@Inject` and `@AssistedInject` constructors visible to this resolver
 */
internal fun Resolver.injectConstructors(): List<Pair<KSClassDeclaration, KSFunctionDeclaration>> {
  return getAnnotatedSymbols<Inject>()
    .plus(getAnnotatedSymbols<AssistedInject>())
    .filterIsInstance<KSFunctionDeclaration>()
    .filter { it.isConstructor() }
    .groupBy {
      it.parentDeclaration as KSClassDeclaration
    }
    .mapNotNull { (clazz, constructors) ->
      requireSingleInjectConstructor(constructors, clazz)

      clazz to constructors[0]
    }
}

private fun requireSingleInjectConstructor(
  constructors: List<KSFunctionDeclaration>,
  clazz: KSClassDeclaration,
) {
  if (constructors.size == 1) {
    return
  }

  val classFqName = clazz.qualifiedName!!.asString()
  val constructorsErrorMessage = constructors.joinToString { constructor ->
    val formattedAnnotations =
      constructor
        .annotations
        .joinToString(" ", postfix = " ") { annotation ->
          val annotationFq = annotation.annotationType.resolve().declaration.qualifiedName
          "@${annotationFq!!.asString()}"
        }
        .replace("@javax.inject.Inject", "@Inject")

    val formattedConstructorParameters =
      constructor
        .parameters
        .joinToString(
          separator = ", ",
          prefix = "(",
          postfix = ")",
          transform = { param ->
            val parameterClass = param.type.resolve().resolveKSClassDeclaration()
            parameterClass!!.simpleName.getShortName()
          },
        )

    formattedAnnotations + classFqName + formattedConstructorParameters
  }
  throw KspAnvilException(
    node = clazz,
    message = "Type $classFqName may only contain one injected " +
      "constructor. Found: [$constructorsErrorMessage]",
  )
}

internal inline fun <reified T> Resolver.getAnnotatedSymbols(): Sequence<KSAnnotated> {
  val clazz = T::class
  val fqcn = clazz.qualifiedName
    ?: throw IllegalArgumentException("Cannot get qualified name for annotation $clazz")
  return getSymbolsWithAnnotation(fqcn)
}

internal fun KSClassDeclaration.withCompanion(): Sequence<KSClassDeclaration> =
  sequence {
    yield(this@withCompanion)
    yieldAll(declarations.filterIsInstance<KSClassDeclaration>().filter { it.isCompanionObject })
  }

internal inline fun <reified T> KSClassDeclaration.getAnnotatedFunctions() =
  getDeclaredFunctions()
    .filter { it.isAnnotationPresent<T>() }

internal fun KSFunctionDeclaration.isExtensionDeclaration(): Boolean =
  extensionReceiver != null

internal fun KSFunctionDeclaration.returnTypeOrNull(): KSType? =
  returnType?.resolve()?.takeIf {
    it.declaration.qualifiedName?.asString() != "kotlin.Unit"
  }
