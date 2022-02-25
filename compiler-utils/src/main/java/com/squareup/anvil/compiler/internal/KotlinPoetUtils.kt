@file:Suppress("unused")

package com.squareup.anvil.compiler.internal

import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.internal.reference.AnnotatedReference
import com.squareup.anvil.compiler.internal.reference.TypeParameterReference
import com.squareup.anvil.compiler.internal.reference.TypeReference
import com.squareup.anvil.compiler.internal.reference.canResolveFqName
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.WildcardTypeName
import com.squareup.kotlinpoet.jvm.jvmSuppressWildcards
import org.jetbrains.kotlin.builtins.isFunctionType
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.PackageFragmentDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFunctionType
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtProjectionKind
import org.jetbrains.kotlin.psi.KtTypeElement
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.resolve.descriptorUtil.parents
import org.jetbrains.kotlin.resolve.descriptorUtil.parentsWithSelf
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.Variance.INVARIANT
import org.jetbrains.kotlin.types.Variance.IN_VARIANCE
import org.jetbrains.kotlin.types.Variance.OUT_VARIANCE
import org.jetbrains.kotlin.types.typeUtil.isTypeParameter
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import java.io.ByteArrayOutputStream

@ExperimentalAnvilApi
public fun KtClassOrObject.asClassName(): ClassName =
  ClassName(
    packageName = containingKtFile.packageFqName.safePackageString(),
    simpleNames = parentsWithSelf
      .filterIsInstance<KtClassOrObject>()
      .map { it.nameAsSafeName.asString() }
      .toList()
      .reversed()
  )

@ExperimentalAnvilApi
public fun ClassDescriptor.asClassName(): ClassName =
  ClassName(
    packageName = parents.filterIsInstance<PackageFragmentDescriptor>().first()
      .fqName.safePackageString(),
    simpleNames = parentsWithSelf.filterIsInstance<ClassDescriptor>()
      .map { it.name.asString() }
      .toList()
      .reversed()
  )

@ExperimentalAnvilApi
public fun FqName.asClassName(module: ModuleDescriptor): ClassName {
  return asClassNameOrNull(module)
    ?: throw AnvilCompilationException("Couldn't parse ClassName for $this.")
}

internal fun FqName.asClassNameOrNull(module: ModuleDescriptor): ClassName? {
  val segments = pathSegments().map { it.asString() }

  // If the first sentence case is not the last segment of the path it becomes ambiguous,
  // for example, com.Foo.Bar could be an inner class Bar or an unconventional package com.Foo.
  val canGuessClassName = segments.indexOfFirst { it[0].isUpperCase() } == segments.size - 1
  if (canGuessClassName) {
    return ClassName.bestGuess(asString())
  }

  for (index in (segments.size - 1) downTo 1) {
    val packageSegments = segments.subList(0, index)
    val classSegments = segments.subList(index, segments.size)

    val validFqName = FqName.fromSegments(packageSegments + classSegments)
      .canResolveFqName(module)

    if (validFqName) {
      return ClassName(
        packageName = packageSegments.joinToString(separator = "."),
        simpleNames = classSegments
      )
    }
  }

  // No matching class found. It's either a package name only or doesn't exist.
  return null
}

@Suppress("DeprecatedCallableAddReplaceWith")
@ExperimentalAnvilApi
@Deprecated("Don't rely on PSI and make the code agnostic to the underlying implementation.")
public fun KtTypeReference.requireTypeName(
  module: ModuleDescriptor
): TypeName {
  fun PsiElement.fail(): Nothing = throw AnvilCompilationException(
    message = "Couldn't resolve type: $text",
    element = this
  )

  fun KtTypeElement.requireTypeName(): TypeName {
    return when (this) {
      is KtUserType -> {
        val className = fqNameOrNull(module)?.asClassName(module)
          ?: if (isTypeParameter()) {
            val bounds = findExtendsBound().map { it.asClassName(module) }
            return TypeVariableName(text, bounds)
          } else {
            throw AnvilCompilationException("Couldn't resolve fqName.", element = this)
          }

        val typeArgumentList = typeArgumentList
        if (typeArgumentList != null) {
          className.parameterizedBy(
            typeArgumentList.arguments.map { typeProjection ->
              if (typeProjection.projectionKind == KtProjectionKind.STAR) {
                STAR
              } else {
                val typeReference = typeProjection.typeReference ?: typeProjection.fail()
                typeReference
                  .requireTypeName(module)
                  .let { typeName ->
                    // Preserve annotations, e.g. List<@JvmSuppressWildcards Abc>.
                    if (typeReference.annotationEntries.isNotEmpty()) {
                      typeName.copy(
                        annotations = typeName.annotations + typeReference.annotationEntries
                          .map { annotationEntry ->
                            AnnotationSpec
                              .builder(
                                annotationEntry
                                  .requireFqName(module)
                                  .asClassName(module)
                              )
                              .build()
                          }
                      )
                    } else {
                      typeName
                    }
                  }
                  .let { typeName ->
                    val modifierList = typeProjection.modifierList
                    when {
                      modifierList == null -> typeName
                      modifierList.hasModifier(KtTokens.OUT_KEYWORD) ->
                        WildcardTypeName.producerOf(typeName)
                      modifierList.hasModifier(KtTokens.IN_KEYWORD) ->
                        WildcardTypeName.consumerOf(typeName)
                      else -> typeName
                    }
                  }
              }
            }
          )
        } else {
          className
        }
      }
      is KtFunctionType ->
        LambdaTypeName.get(
          receiver = receiver?.typeReference?.requireTypeName(module),
          parameters = parameterList
            ?.parameters
            ?.map { parameter ->
              val parameterReference = parameter.typeReference ?: parameter.fail()
              ParameterSpec.unnamed(parameterReference.requireTypeName(module))
            }
            ?: emptyList(),
          returnType = (returnTypeReference ?: fail())
            .requireTypeName(module)
        )
      is KtNullableType -> {
        (innerType ?: fail()).requireTypeName().copy(nullable = true)
      }
      else -> fail()
    }
  }

  return (typeElement ?: fail()).requireTypeName()
}

@ExperimentalAnvilApi
public fun ClassId.asClassName(): ClassName {
  return ClassName(
    packageName = packageFqName.asString(),
    simpleNames = relativeClassName.pathSegments().map { it.asString() }
  )
}

@Suppress("DeprecatedCallableAddReplaceWith")
@ExperimentalAnvilApi
@Deprecated("Don't rely on PSI and make the code agnostic to the underlying implementation.")
public fun KotlinType.asTypeName(): TypeName {
  return asTypeNameOrNull { true }!!
}

/**
 * @param rawTypeFilter an optional raw type filter to allow for
 *                      short-circuiting this before attempting to
 *                      resolve type arguments.
 */
@ExperimentalAnvilApi
@Suppress("DeprecatedCallableAddReplaceWith")
@Deprecated(
  "Don't rely on descriptors and make the code agnostic to the underlying implementation."
)
public fun KotlinType.asTypeNameOrNull(
  rawTypeFilter: (ClassName) -> Boolean = { true }
): TypeName? {
  if (isTypeParameter()) return TypeVariableName(toString())

  val className = classDescriptor().asClassName()
  if (!rawTypeFilter(className)) {
    return null
  }
  if (arguments.isEmpty()) return className.copy(nullable = isMarkedNullable)

  val argumentTypeNames = arguments.map { typeProjection ->
    if (typeProjection.isStarProjection) {
      STAR
    } else {
      val typeName = typeProjection.type.asTypeName()
      when (typeProjection.projectionKind) {
        INVARIANT -> typeName
        OUT_VARIANCE -> WildcardTypeName.producerOf(typeName)
        IN_VARIANCE -> WildcardTypeName.consumerOf(typeName)
      }
    }
  }

  return className.parameterizedBy(argumentTypeNames).copy(nullable = isMarkedNullable)
}

@ExperimentalAnvilApi
@Suppress("DeprecatedCallableAddReplaceWith")
@Deprecated(
  "Don't rely on descriptors and make the code agnostic to the underlying implementation."
)
public fun <T : KtCallableDeclaration> TypeName.withJvmSuppressWildcardsIfNeeded(
  callableDeclaration: T,
  module: ModuleDescriptor
): TypeName {
  // If the parameter is annotated with @JvmSuppressWildcards, then add the annotation
  // to our type so that this information is forwarded when our Factory is compiled.
  val hasJvmSuppressWildcards =
    callableDeclaration.typeReference?.hasAnnotation(jvmSuppressWildcardsFqName, module) ?: false

  // Add the @JvmSuppressWildcards annotation even for simple generic return types like
  // Set<String>. This avoids some edge cases where Dagger chokes.
  val isGenericType = callableDeclaration.typeReference?.isGenericType() ?: false

  // Same for functions.
  val isFunctionType = callableDeclaration.typeReference?.isFunctionType() ?: false

  return when {
    hasJvmSuppressWildcards || isGenericType -> this.jvmSuppressWildcards()
    isFunctionType -> this.jvmSuppressWildcards()
    else -> this
  }
}

@ExperimentalAnvilApi
public fun TypeName.withJvmSuppressWildcardsIfNeeded(
  annotatedReference: AnnotatedReference,
  typeReference: TypeReference
): TypeName {
  // If the parameter is annotated with @JvmSuppressWildcards, then add the annotation
  // to our type so that this information is forwarded when our Factory is compiled.
  val hasJvmSuppressWildcards = annotatedReference.isAnnotatedWith(jvmSuppressWildcardsFqName)

  // Add the @JvmSuppressWildcards annotation even for simple generic return types like
  // Set<String>. This avoids some edge cases where Dagger chokes.
  val isGenericType = typeReference.isGenericType()

  // Same for functions.
  val isFunctionType = typeReference.isFunctionType()

  return when {
    hasJvmSuppressWildcards || isGenericType -> this.jvmSuppressWildcards()
    isFunctionType -> this.jvmSuppressWildcards()
    else -> this
  }
}

@ExperimentalAnvilApi
@Suppress("DeprecatedCallableAddReplaceWith")
@Deprecated(
  "Don't rely on descriptors and make the code agnostic to the underlying implementation."
)
public fun TypeName.withJvmSuppressWildcardsIfNeeded(
  callableMemberDescriptor: CallableMemberDescriptor
): TypeName {
  // If the parameter is annotated with @JvmSuppressWildcards, then add the annotation
  // to our type so that this information is forwarded when our Factory is compiled.
  val hasJvmSuppressWildcards = callableMemberDescriptor.annotations
    .hasAnnotation(jvmSuppressWildcardsFqName)

  // Add the @JvmSuppressWildcards annotation even for simple generic return types like
  // Set<String>. This avoids some edge cases where Dagger chokes.
  val isGenericType = callableMemberDescriptor.typeParameters.isNotEmpty()

  val type = callableMemberDescriptor.safeAs<PropertyDescriptor>()?.type
    ?: callableMemberDescriptor.valueParameters.first().type

  // Same for functions.
  val isFunctionType = type.isFunctionType

  return when {
    hasJvmSuppressWildcards || isGenericType -> this.jvmSuppressWildcards()
    isFunctionType -> this.jvmSuppressWildcards()
    else -> this
  }
}

private fun FileSpec.writeToString(): String {
  val stream = ByteArrayOutputStream()
  stream.writer().use {
    writeTo(it)
  }
  return stream.toString()
}

private fun FileSpec.Builder.suppressWarnings() {
  addAnnotation(
    AnnotationSpec
      .builder(Suppress::class)
      // Suppress deprecation warnings.
      .addMember("\"DEPRECATION\"")
      // Suppress errors for experimental features in generated code.
      .apply {
        if (KotlinVersion.CURRENT > KotlinVersion(1, 6, 10)) {
          addMember("\"OPT_IN_USAGE\"")
          addMember("\"OPT_IN_USAGE_ERROR\"")
        } else {
          addMember("\"EXPERIMENTAL_API_USAGE_ERROR\"")
          addMember("\"EXPERIMENTAL_API_USAGE\"")
        }
      }
      .build()
  )
}

@ExperimentalAnvilApi
public fun FileSpec.Companion.buildFile(
  packageName: String,
  fileName: String,
  generatorComment: String = "Generated by Anvil.\nhttps://github.com/square/anvil",
  block: FileSpec.Builder.() -> Unit
): String =
  builder(packageName, fileName)
    .apply {
      // Suppress any deprecation warnings.
      suppressWarnings()

      block()
    }
    .addComment(generatorComment)
    .build()
    .writeToString()

@ExperimentalAnvilApi
public fun ClassName.optionallyParameterizedBy(
  typeParameters: List<TypeParameterReference>
): TypeName {
  return if (typeParameters.isEmpty()) {
    this
  } else {
    parameterizedBy(typeParameters.map { it.typeVariableName })
  }
}
