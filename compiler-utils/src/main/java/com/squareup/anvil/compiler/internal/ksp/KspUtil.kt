package com.squareup.anvil.compiler.internal.ksp

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.containingFile
import com.google.devtools.ksp.getClassDeclarationByName
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
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.NonExistLocation
import com.squareup.anvil.compiler.internal.fqName
import com.squareup.anvil.compiler.internal.mergeComponentFqName
import com.squareup.anvil.compiler.internal.mergeInterfacesFqName
import com.squareup.anvil.compiler.internal.mergeModulesFqName
import com.squareup.anvil.compiler.internal.mergeSubcomponentFqName
import com.squareup.anvil.compiler.internal.reference.asClassId
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.jvm.jvmSuppressWildcards
import com.squareup.kotlinpoet.ksp.TypeParameterResolver
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
public fun <T : Annotation> KSAnnotated.getKSAnnotationsByType(
  annotationKClass: KClass<T>,
): Sequence<KSAnnotation> {
  val qualifiedName = annotationKClass.qualifiedName ?: return emptySequence()
  return getKSAnnotationsByQualifiedName(qualifiedName)
}

/**
 * Returns a sequence of [KSAnnotations][KSAnnotation] of the given [qualifiedName].
 */
public fun KSAnnotated.getKSAnnotationsByQualifiedName(
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

public fun KSAnnotated.isAnnotationPresent(qualifiedName: String): Boolean =
  getKSAnnotationsByQualifiedName(qualifiedName).firstOrNull() != null

public inline fun <reified T> KSAnnotated.isAnnotationPresent(): Boolean {
  return isAnnotationPresent(T::class)
}

public fun KSAnnotated.isAnnotationPresent(klass: KClass<*>): Boolean {
  val fqcn = klass.qualifiedName ?: return false
  return getKSAnnotationsByQualifiedName(fqcn).firstOrNull() != null
}

public fun KSClassDeclaration.isAnnotationClass(): Boolean = classKind == ANNOTATION_CLASS
public fun KSModifierListOwner.isLateInit(): Boolean = Modifier.LATEINIT in modifiers

@OptIn(KspExperimental::class)
public fun TypeName.withJvmSuppressWildcardsIfNeeded(
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
public fun KSType.resolveKSClassDeclaration(): KSClassDeclaration? =
  declaration.resolveKSClassDeclaration()

/**
 * Resolves the [KSClassDeclaration] representation of this declaration, including following
 * typealiases as needed.
 *
 * [KSTypeParameter] types will return null. If you expect one here, you should check the
 * declaration directly.
 */
public fun KSDeclaration.resolveKSClassDeclaration(): KSClassDeclaration? {
  return when (val declaration = unwrapTypealiases()) {
    is KSClassDeclaration -> declaration
    is KSTypeParameter -> null
    else -> error("Unexpected declaration type: $this")
  }
}

/**
 * Returns the resolved declaration following any typealiases.
 */
public tailrec fun KSDeclaration.unwrapTypealiases(): KSDeclaration = when (this) {
  is KSTypeAlias -> type.resolve().declaration.unwrapTypealiases()
  else -> this
}

/**
 * Returns a sequence of all `@Inject` and `@AssistedInject` constructors visible to this resolver
 */
public fun Resolver.injectConstructors(): List<Pair<KSClassDeclaration, KSFunctionDeclaration>> {
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

public inline fun <reified T> Resolver.getAnnotatedSymbols(): Sequence<KSAnnotated> {
  val clazz = T::class
  val fqcn = clazz.qualifiedName
    ?: throw IllegalArgumentException("Cannot get qualified name for annotation $clazz")
  return getSymbolsWithAnnotation(fqcn)
}

public fun KSClassDeclaration.withCompanion(): Sequence<KSClassDeclaration> =
  sequence {
    yield(this@withCompanion)
    yieldAll(declarations.filterIsInstance<KSClassDeclaration>().filter { it.isCompanionObject })
  }

public inline fun <reified T> KSClassDeclaration.getAnnotatedFunctions(): Sequence<KSFunctionDeclaration> =
  getDeclaredFunctions()
    .filter { it.isAnnotationPresent<T>() }

public fun KSFunctionDeclaration.isExtensionDeclaration(): Boolean =
  extensionReceiver != null

public fun KSFunctionDeclaration.returnTypeOrNull(): KSType? =
  returnType?.resolve()?.takeIf {
    it.declaration.qualifiedName?.asString() != "kotlin.Unit"
  }

public fun KSFunction.returnTypeOrNull(): KSType? =
  returnType?.takeIf {
    it.declaration.qualifiedName?.asString() != "kotlin.Unit"
  }

public fun Resolver.getClassesWithAnnotations(
  vararg annotations: FqName,
): Sequence<KSClassDeclaration> = getClassesWithAnnotations(annotations.map { it.asString() })

public fun Resolver.getClassesWithAnnotations(
  annotations: Collection<String>,
): Sequence<KSClassDeclaration> = annotations.asSequence()
  .flatMap(::getSymbolsWithAnnotation)
  .filterIsInstance<KSClassDeclaration>()
  .distinctBy { it.qualifiedName?.asString() }

public fun Resolver.anySymbolsWithAnnotations(
  annotations: Collection<String>,
): Boolean {
  return annotations.any { getSymbolsWithAnnotation(it).any() }
}

public fun KSAnnotated.findAll(vararg annotations: String): List<KSAnnotation> {
  return annotations.flatMap { annotation ->
    getKSAnnotationsByQualifiedName(annotation)
  }
}

public val KSAnnotation.declaringClass: KSClassDeclaration get() = parent as KSClassDeclaration

public fun KSAnnotated.find(
  annotationName: String,
  scopeName: KSType? = null,
): List<KSAnnotation> {
  return findAll(annotationName)
    .filter {
      scopeName == null || it.scopeOrNull() == scopeName
    }
}

public fun KSClassDeclaration.atLeastOneAnnotation(
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

public val KSClassDeclaration.classId: ClassId get() = toClassName().asClassId()

public fun KSFunctionDeclaration.toFunSpec(): FunSpec {
  val builder = FunSpec.builder(simpleName.getShortName())
    .addModifiers(modifiers.mapNotNull { it.toKModifier() })
    .addAnnotations(
      resolvableAnnotations
        .map { it.toAnnotationSpec() }.asIterable(),
    )

  returnType?.contextualToTypeName()?.let { builder.returns(it) }

  for (parameter in parameters) {
    builder.addParameter(parameter.toParameterSpec())
  }

  return builder.build()
}

public fun KSPropertyDeclaration.toPropertySpec(
  typeOverride: TypeName = type.contextualToTypeName(),
): PropertySpec {
  return PropertySpec.builder(simpleName.getShortName(), typeOverride)
    .addModifiers(modifiers.mapNotNull { it.toKModifier() })
    .addAnnotations(
      resolvableAnnotations.map { it.toAnnotationSpec() }.asIterable(),
    )
    .build()
}

public fun KSValueParameter.toParameterSpec(): ParameterSpec {
  return ParameterSpec.builder(name!!.asString(), type.contextualToTypeName())
    .addAnnotations(
      resolvableAnnotations.map { it.toAnnotationSpec() }.asIterable(),
    )
    .build()
}

public fun KSAnnotated.mergeAnnotations(): List<KSAnnotation> {
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
public val KSAnnotated.resolvableAnnotations: Sequence<KSAnnotation>
  get() = annotations
    .filterNot { it.annotationType.resolve().isError }

public val KSAnnotation.fqName: FqName
  get() =
    annotationType.resolve().resolveKSClassDeclaration()!!.fqName
public val KSType.fqName: FqName get() = resolveKSClassDeclaration()!!.fqName
public val KSClassDeclaration.fqName: FqName
  get() {
    // Call resolveKSClassDeclaration to ensure we follow typealiases
    return resolveKSClassDeclaration()!!
      .toClassName()
      .fqName
  }

/**
 * A contextual alternative to [KSTypeReference.toTypeName] that uses [KSType.contextualToTypeName]
 * under the hood.
 */
public fun KSTypeReference.contextualToTypeName(
  typeParamResolver: TypeParameterResolver = TypeParameterResolver.EMPTY,
): TypeName {
  return resolve().contextualToTypeName(this, typeParamResolver)
}

/**
 * A contextual alternative to [KSType.toTypeName] that requires an [origin] param to better
 * indicate the origin of the error type.
 */
public fun KSType.contextualToTypeName(
  origin: KSNode,
  typeParamResolver: TypeParameterResolver = TypeParameterResolver.EMPTY,
): TypeName {
  checkErrorType(origin)
  return toTypeName(typeParamResolver)
}

/**
 * A contextual alternative to `KSTypeReference.resolve().toClassName()` that uses
 * [KSType.contextualToClassName] under the hood.
 */
public fun KSTypeReference.contextualToClassName(): ClassName {
  return resolve().contextualToClassName(this)
}

/**
 * A contextual alternative to [KSType.toClassName] that requires an [origin] param to better
 * indicate the origin of the error type.
 */
public fun KSType.contextualToClassName(origin: KSNode): ClassName {
  checkErrorType(origin)
  return toClassName()
}

private fun KSType.checkErrorType(origin: KSNode) {
  val (type, node) = if (isError) {
    this to origin
  } else {
    arguments.asSequence()
      .mapNotNull {
        it.type?.let {
          it.resolve() to it
        }
      }
      .firstOrNull { it.first.isError }
  } ?: return

  val message = buildString {
    appendLine(
      "Error type '$type' is not resolvable in the current round of processing.",
    )
    when (origin) {
      is KSValueParameter -> {
        appendLine(
          "This happened for parameter '${origin.name?.asString()}: $type'.",
        )
      }
      is KSPropertyDeclaration -> {
        appendLine(
          "This happened for property '${origin.simpleName.getShortName()}: $type'.",
        )
      }
      is KSFunctionDeclaration -> {
        appendLine("This happened for function '${origin.simpleName.getShortName()}: $type'.")
      }
    }
    append(
      "Check for missing or broken imports",
    )
    origin.containingFile?.fileName?.let {
      append(" in $it")
    }
    appendLine('.')
    appendLine(
      "If this is a generated type, ensure the tool that generates it has its outputs appropriately sourced as inputs to the KSP task.",
    )
  }
  val finalNode = node.takeUnless { it.location == NonExistLocation } ?: origin
  throw KspAnvilException(message = message, node = finalNode)
}

public val KSFunctionDeclaration.reportableReturnTypeNode: KSNode
  get() = returnType ?: this

public fun Resolver.getClassDeclarationByName(className: ClassName): KSClassDeclaration? {
  return getClassDeclarationByName(className.canonicalName)
}

public fun Resolver.requireClassDeclaration(
  className: ClassName,
  node: (() -> KSNode)?,
): KSClassDeclaration {
  return getClassDeclarationByName(className)
    ?: run {
      val message = "Could not find class '$className'"
      if (node != null) {
        throw KspAnvilException(
          message = message,
          node = node(),
        )
      } else {
        error(message)
      }
    }
}

public fun Resolver.getClassDeclarationByName(fqName: FqName): KSClassDeclaration? {
  return getClassDeclarationByName(fqName.asString())
}

public fun Resolver.requireClassDeclaration(fqName: FqName, node: KSNode?): KSClassDeclaration {
  return getClassDeclarationByName(fqName)
    ?: run {
      val message = "Could not find class '${fqName.asString()}'"
      if (node != null) {
        throw KspAnvilException(
          message = message,
          node = node,
        )
      } else {
        error(message)
      }
    }
}
