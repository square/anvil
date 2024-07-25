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
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFunction
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSModifierListOwner
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.anvil.compiler.fqName
import com.squareup.anvil.compiler.internal.reference.asClassId
import com.squareup.anvil.compiler.mergeComponentFqName
import com.squareup.anvil.compiler.mergeInterfacesFqName
import com.squareup.anvil.compiler.mergeModulesFqName
import com.squareup.anvil.compiler.mergeSubcomponentFqName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.jvm.jvmSuppressWildcards
import com.squareup.kotlinpoet.ksp.toAnnotationSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toKModifier
import com.squareup.kotlinpoet.ksp.toTypeName
import dagger.assisted.AssistedInject
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import javax.inject.Inject
import kotlin.reflect.KClass

/**
 * Returns a sequence of [KSAnnotations][KSAnnotation] of the given [annotationKClass] type.
 */
internal fun <T : Annotation> KSAnnotated.getKSAnnotationsByType(
  annotationKClass: KClass<T>,
): Sequence<KSAnnotation> {
  val qualifiedName = annotationKClass.qualifiedName ?: return emptySequence()
  return getKSAnnotationsByQualifiedName(qualifiedName)
}

/**
 * Returns a sequence of [KSAnnotations][KSAnnotation] of the given [qualifiedName].
 */
internal fun KSAnnotated.getKSAnnotationsByQualifiedName(
  qualifiedName: String,
): Sequence<KSAnnotation> {
  // Don't use resolvableAnnotations here to save the double resolve() call
  return annotations.filter {
    // Don't check the simple name as it could be a typealias
    val type = it.annotationType.resolve()

    // Ignore error types
    if (type.isError) return@filter false

    // Resolve the KSClassDeclaration to ensure we peek through typealiases
    type.resolveKSClassDeclaration()?.qualifiedName?.asString() == qualifiedName
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
internal fun KSType.resolveKSClassDeclaration(): KSClassDeclaration? =
  declaration.resolveKSClassDeclaration()

/**
 * Resolves the [KSClassDeclaration] representation of this declaration, including following
 * typealiases as needed.
 *
 * [KSTypeParameter] types will return null. If you expect one here, you should check the
 * declaration directly.
 */
internal fun KSDeclaration.resolveKSClassDeclaration(): KSClassDeclaration? {
  return when (val declaration = unwrapTypealiases()) {
    is KSClassDeclaration -> declaration
    is KSTypeParameter -> null
    else -> error("Unexpected declaration type: $this")
  }
}

/**
 * Returns the resolved declaration following any typealiases.
 */
internal tailrec fun KSDeclaration.unwrapTypealiases(): KSDeclaration = when (this) {
  is KSTypeAlias -> type.resolve().declaration.unwrapTypealiases()
  else -> this
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
        .resolvableAnnotations
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

internal fun KSFunction.returnTypeOrNull(): KSType? =
  returnType?.takeIf {
    it.declaration.qualifiedName?.asString() != "kotlin.Unit"
  }

internal fun Resolver.getSymbolsWithAnnotations(
  vararg annotations: FqName,
): Sequence<KSAnnotated> = annotations.asSequence()
  .flatMap {
    getSymbolsWithAnnotation(it.asString())
  }
  .distinct()

internal fun KSAnnotated.findAll(vararg annotations: String): List<KSAnnotation> {
  return annotations.flatMap { annotation ->
    getKSAnnotationsByQualifiedName(annotation)
  }
}

internal val KSAnnotation.declaringClass: KSClassDeclaration get() = parent as KSClassDeclaration

internal fun KSAnnotated.find(
  annotationName: String,
  scopeName: KSType? = null,
): List<KSAnnotation> {
  return findAll(annotationName)
    .filter {
      scopeName == null || it.scopeOrNull() == scopeName
    }
}

internal fun KSClassDeclaration.atLeastOneAnnotation(
  annotationName: String,
  scopeName: KSType? = null,
): List<KSAnnotation> {
  return find(annotationName = annotationName, scopeName = scopeName)
    .ifEmpty {
      throw KspAnvilException(
        node = this,
        message = "Class ${qualifiedName?.asString()} is not annotated with $annotationName" +
          "${if (scopeName == null) "" else " with scope $scopeName"}.",
      )
    }
}

internal val KSClassDeclaration.classId: ClassId get() = toClassName().asClassId()

internal fun KSFunctionDeclaration.toFunSpec(): FunSpec {
  val builder = FunSpec.builder(simpleName.getShortName())
    .addModifiers(modifiers.mapNotNull { it.toKModifier() })
    .addAnnotations(
      resolvableAnnotations
        .map { it.toAnnotationSpec() }.asIterable(),
    )

  returnType?.resolve()?.toTypeName()?.let { builder.returns(it) }

  for (parameter in parameters) {
    builder.addParameter(parameter.toParameterSpec())
  }

  return builder.build()
}

internal fun KSPropertyDeclaration.toPropertySpec(): PropertySpec {
  return PropertySpec.builder(simpleName.getShortName(), type.resolve().toTypeName())
    .addModifiers(modifiers.mapNotNull { it.toKModifier() })
    .addAnnotations(
      resolvableAnnotations.map { it.toAnnotationSpec() }.asIterable(),
    )
    .build()
}

internal fun KSValueParameter.toParameterSpec(): ParameterSpec {
  return ParameterSpec.builder(name!!.asString(), type.resolve().toTypeName())
    .addAnnotations(
      resolvableAnnotations.map { it.toAnnotationSpec() }.asIterable(),
    )
    .build()
}

internal fun KSAnnotated.mergeAnnotations(): List<KSAnnotation> {
  return findAll(
    mergeComponentFqName.asString(),
    mergeSubcomponentFqName.asString(),
    mergeModulesFqName.asString(),
    mergeInterfacesFqName.asString(),
  )
}

/**
 * Returns a sequence of [KSAnnotation] types that are not error types.
 */
internal val KSAnnotated.resolvableAnnotations: Sequence<KSAnnotation> get() = annotations
  .filterNot { it.annotationType.resolve().isError }

internal val KSAnnotation.fqName: FqName get() =
  annotationType.resolve().resolveKSClassDeclaration()!!.fqName
internal val KSType.fqName: FqName get() = resolveKSClassDeclaration()!!.fqName
internal val KSClassDeclaration.fqName: FqName get() {
  // Call resolveKSClassDeclaration to ensure we follow typealiases
  return resolveKSClassDeclaration()!!
    .toClassName()
    .fqName
}
