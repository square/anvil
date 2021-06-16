@file:Suppress("unused")

package com.squareup.anvil.compiler.internal

import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.WildcardTypeName
import com.squareup.kotlinpoet.jvm.jvmSuppressWildcards
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.PackageFragmentDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.lexer.KtTokens
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
import org.jetbrains.kotlin.resolve.constants.EnumValue
import org.jetbrains.kotlin.resolve.constants.KClassValue
import org.jetbrains.kotlin.resolve.descriptorUtil.parents
import org.jetbrains.kotlin.resolve.descriptorUtil.parentsWithSelf
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.isTypeParameter
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
  val segments = pathSegments().map { it.asString() }

  // If the first sentence case is not the last segment of the path it becomes ambiguous,
  // for example, com.Foo.Bar could be a inner class Bar or an unconventional package com.Foo.
  val canGuessClassName = segments.indexOfFirst { it[0].isUpperCase() } == segments.size - 1
  if (canGuessClassName) {
    return ClassName.bestGuess(asString())
  }

  for (index in (segments.size - 1) downTo 1) {
    val packageSegments = segments.subList(0, index)
    val classSegments = segments.subList(index, segments.size)

    val validFqName = module.canResolveFqName(
      packageName = FqName.fromSegments(packageSegments),
      className = classSegments.joinToString(separator = ".")
    )

    if (validFqName) {
      return ClassName(
        packageName = packageSegments.joinToString(separator = "."),
        simpleNames = classSegments
      )
    }
  }

  throw AnvilCompilationException("Couldn't parse ClassName for $this.")
}

@ExperimentalAnvilApi
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
public fun KotlinType.asTypeName(): TypeName {
  return asTypeNameOrNull { true }!!
}

/**
 * @param rawTypeFilter an optional raw type filter to allow for
 *                      short-circuiting this before attempting to
 *                      resolve type arguments.
 */
@ExperimentalAnvilApi
public fun KotlinType.asTypeNameOrNull(
  rawTypeFilter: (ClassName) -> Boolean = { true }
): TypeName? {
  if (isTypeParameter()) return TypeVariableName(toString())

  val className = classDescriptorForType().asClassName()
  if (!rawTypeFilter(className)) {
    return null
  }
  if (arguments.isEmpty()) return className.copy(nullable = isMarkedNullable)

  val argumentTypeNames = arguments.map { typeProjection ->
    if (typeProjection.isStarProjection) {
      STAR
    } else {
      typeProjection.type.asTypeName()
    }
  }

  return className.parameterizedBy(argumentTypeNames).copy(nullable = isMarkedNullable)
}

@ExperimentalAnvilApi
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

private fun FileSpec.writeToString(): String {
  val stream = ByteArrayOutputStream()
  stream.writer().use {
    writeTo(it)
  }
  return stream.toString()
}

private fun FileSpec.Builder.suppressWarnings() {
  addAnnotation(AnnotationSpec.builder(Suppress::class).addMember("\"DEPRECATION\"").build())
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
public fun AnnotationDescriptor.toAnnotationSpec(module: ModuleDescriptor): AnnotationSpec {
  return AnnotationSpec
    .builder(requireClass().asClassName())
    .apply {
      allValueArguments.forEach { (name, value) ->
        when (value) {
          is KClassValue -> {
            val className = value.argumentType(module).classDescriptorForType()
              .asClassName()
            addMember("${name.asString()} = %T::class", className)
          }
          is EnumValue -> {
            val enumMember = MemberName(
              enclosingClassName = value.enumClassId.asSingleFqName()
                .asClassName(module),
              simpleName = value.enumEntryName.asString()
            )
            addMember("${name.asString()} = %M", enumMember)
          }
          // String, int, long, ... other primitives.
          else -> addMember("${name.asString()} = $value")
        }
      }
    }
    .build()
}
