@file:Suppress("unused")

package com.squareup.anvil.compiler.internal

import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName
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
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.fir.lightTree.converter.nameAsSafeName
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassLiteralExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtCollectionLiteralExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunctionType
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProjectionKind
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtTypeElement
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.resolve.constants.EnumValue
import org.jetbrains.kotlin.resolve.constants.KClassValue
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

private fun FqName.asClassNameOrNull(module: ModuleDescriptor): ClassName? {
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

  // No matching class found. It's either a package name only or doesn't exist.
  return null
}

@ExperimentalAnvilApi
public fun FqName.asMemberName(module: ModuleDescriptor): MemberName {
  val segments = pathSegments().map { it.asString() }
  val simpleName = segments.last()
  val prefixFqName = FqName.fromSegments(segments.dropLast(1))
  return prefixFqName.asClassNameOrNull(module)?.let {
    val classOrObj = module.getKtClassOrObjectOrNull(prefixFqName)
    val imported = if (classOrObj is KtClass) {
      // Must be in a companion, check its name
      // We do this because accessors could be just Foo.CONSTANT but to member import it we need
      // to include the companion path
      val companionName = classOrObj.companionObjects.first().name ?: "Companion"
      it.nestedClass(companionName)
    } else {
      it
    }
    MemberName(imported, simpleName)
  } ?: MemberName(prefixFqName.pathSegments().joinToString("."), simpleName)
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

/**
 * Converts a lambda `TypeName` to a standard `kotlin.Function*` type.
 *
 * given the lambda type: `(kotlin.String) -> kotlin.Unit`
 * returns the function type: `kotlin.Function1<String, Unit>`
 */
@ExperimentalAnvilApi
public fun LambdaTypeName.asFunctionType(): ParameterizedTypeName {

  val allTypes = listOfNotNull(receiver)
    .plus(parameters.map { it.type })
    .plus(returnType)

  return ClassName("kotlin", "Function${allTypes.size - 1}")
    .parameterizedBy(allTypes)
}

@ExperimentalAnvilApi
public fun ClassId.asClassName(): ClassName {
  return ClassName(
    packageName = packageFqName.asString(),
    simpleNames = relativeClassName.pathSegments().map { it.asString() }
  )
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

  val className = requireClassDescriptor().asClassName()
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
            val className = value.argumentType(module).requireClassDescriptor()
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

/**
 * Returns a filtered list of [javax.inject.Qualifier]-annotated [entries][KtAnnotationEntry] as
 * a [AnnotationSpecs][AnnotationSpec].
 */
@ExperimentalAnvilApi
public fun List<KtAnnotationEntry>.qualifierAnnotationSpecs(
  module: ModuleDescriptor
): List<AnnotationSpec> = mapNotNull { annotationEntry ->
  if (!annotationEntry.isQualifier(module)) {
    null
  } else {
    annotationEntry.toAnnotationSpec(module)
  }
}

/** Returns an [AnnotationSpec] representation of this [KtAnnotationEntry]. */
@ExperimentalAnvilApi
public fun KtAnnotationEntry.toAnnotationSpec(
  module: ModuleDescriptor
): AnnotationSpec {
  return AnnotationSpec.builder(requireFqName(module).asClassName(module))
    .apply {
      valueArguments
        .filterIsInstance<KtValueArgument>()
        .mapNotNull { valueArgument ->
          valueArgument.getArgumentExpression()?.codeBlock(module)
        }.forEach {
          addMember(it)
        }
    }
    .build()
}

private fun KtExpression.codeBlock(module: ModuleDescriptor): CodeBlock {
  return when (this) {
    // MyClass::class
    is KtClassLiteralExpression -> {
      val className = requireFqName(module).asClassName(module)
      CodeBlock.of("%T::class", className)
    }
    // enums or qualified references
    is KtNameReferenceExpression, is KtQualifiedExpression -> {
      val fqName = try {
        requireFqName(module)
      } catch (e: AnvilCompilationException) {
        if (this is KtNameReferenceExpression) {
          null
        } else {
          throw e
        }
      }
      if (fqName != null) {
        if (module.canResolveFqName(fqName)) {
          CodeBlock.of("%T", fqName.asClassName(module))
        } else {
          CodeBlock.of("%M", fqName.asMemberName(module))
        }
      } else {
        // It's (hopefully) an in-scope constant, look up the hierarchy.
        val ref = (this as KtNameReferenceExpression).getReferencedName()
        findConstantDefinitionInScope(ref)
          ?.asMemberName(module)
          ?.let { memberRef ->
            CodeBlock.of("%M", memberRef)
          }
          // If we reach here, it's a top-level constant defined in the same package but a different
          // file. In this case, we can just do the same in our generated code because we're in the
          // same package and can play by the same rules.
          ?: CodeBlock.of("%L", ref)
      }
    }
    is KtCollectionLiteralExpression -> CodeBlock.of(
      getInnerExpressions()
        .map { it.codeBlock(module) }
        .joinToString(prefix = "[", postfix = "]")
    )
    // literals
    else -> CodeBlock.of("%L", text)
  }
}

private fun List<KtProperty>.containsConstPropertyWithName(name: String): Boolean {
  return any { property ->
    property.hasModifier(KtTokens.CONST_KEYWORD) && property.name == name
  }
}

private fun PsiElement.findConstantDefinitionInScope(name: String): FqName? {
  when (this) {
    is KtProperty, is KtNamedFunction -> {
      // Function or Property, traverse up
      return (this as KtElement).containingClass()?.findConstantDefinitionInScope(name)
        ?: containingFile.findConstantDefinitionInScope(name)
    }
    is KtObjectDeclaration -> {
      if (properties(includeCompanionObjects = false).containsConstPropertyWithName(name)) {
        return requireFqName().child(name.nameAsSafeName())
      } else if (isCompanion()) {
        // Nowhere else to look and don't try to traverse up because this is only looked at from a
        // class already.
        return null
      }
    }
    is KtFile -> {
      if (children.filterIsInstance<KtProperty>().containsConstPropertyWithName(name)) {
        return packageFqName.child(name.nameAsSafeName())
      }
    }
    is KtClass -> {
      // Look in companion object or traverse up
      return companionObjects.asSequence()
        .mapNotNull { it.findConstantDefinitionInScope(name) }
        .firstOrNull()
        ?: containingClass()?.findConstantDefinitionInScope(name)
        ?: containingKtFile.findConstantDefinitionInScope(name)
    }
  }

  return parent?.findConstantDefinitionInScope(name)
}
