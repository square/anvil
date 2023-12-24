package com.squareup.anvil.compiler.codegen.ksp

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.isAnnotationPresent
import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind.ANNOTATION_CLASS
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSModifierListOwner
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.anvil.compiler.assistedInjectFqName
import com.squareup.anvil.compiler.injectFqName
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
internal fun TypeName.withJvmSuppressWildcardsIfNeeded(
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
  return getSymbolsWithAnnotation(injectFqName.asString())
    .plus(getSymbolsWithAnnotation(assistedInjectFqName.asString()))
    .filterIsInstance<KSFunctionDeclaration>()
    .filter { it.isConstructor() }
    .groupBy {
      it.parentDeclaration as KSClassDeclaration
    }
    .mapNotNull { (clazz, constructors) ->
      if (constructors.size != 1) {
        val constructorsErrorMessage = constructors.joinToString { constructor ->
          constructor.annotations.joinToString(" ", postfix = " ") { annotation ->
            "@${annotation.annotationType.resolve().declaration.qualifiedName!!.asString()}"
          }
            .replace("@javax.inject.Inject", "@Inject") +
            clazz.qualifiedName!!.asString() + constructor.parameters.joinToString(
              ", ",
              prefix = "(",
              postfix = ")",
            ) { param ->
              param.type.resolve().resolveKSClassDeclaration()!!.simpleName.getShortName()
            }
        }
        throw KspAnvilException(
          node = clazz,
          message = "Type ${clazz.qualifiedName!!.asString()} may only contain one injected " +
            "constructor. Found: [$constructorsErrorMessage]",
        )
      }

      clazz to constructors[0]
    }
}

internal fun KSNode.parentSequence(): Sequence<KSNode> = generateSequence(this) { it.parent }

internal fun KSNode.declaringClass(): KSClassDeclaration {
  return parentSequence().firstNotNullOf { node ->
    when (node) {
      is KSClassDeclaration -> node
      else -> null
    }
  }
}
