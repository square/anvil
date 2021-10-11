package com.squareup.anvil.compiler.codegen

import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.assistedFqName
import com.squareup.anvil.compiler.codegen.dagger.isSetterInjected
import com.squareup.anvil.compiler.daggerDoubleCheckFqNameString
import com.squareup.anvil.compiler.daggerLazyFqName
import com.squareup.anvil.compiler.internal.asClassName
import com.squareup.anvil.compiler.internal.asMemberName
import com.squareup.anvil.compiler.internal.capitalize
import com.squareup.anvil.compiler.internal.findAnnotation
import com.squareup.anvil.compiler.internal.fqNameOrNull
import com.squareup.anvil.compiler.internal.generateClassName
import com.squareup.anvil.compiler.internal.isNullable
import com.squareup.anvil.compiler.internal.isQualifier
import com.squareup.anvil.compiler.internal.properties
import com.squareup.anvil.compiler.internal.requireFqName
import com.squareup.anvil.compiler.internal.requireTypeName
import com.squareup.anvil.compiler.internal.requireTypeReference
import com.squareup.anvil.compiler.internal.withJvmSuppressWildcardsIfNeeded
import com.squareup.anvil.compiler.providerFqName
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asClassName
import dagger.Lazy
import dagger.internal.ProviderOfLazy
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.fir.lightTree.converter.nameAsSafeName
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassLiteralExpression
import org.jetbrains.kotlin.psi.KtCollectionLiteralExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtTypeArgumentList
import org.jetbrains.kotlin.psi.KtTypeElement
import org.jetbrains.kotlin.psi.KtTypeProjection
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import javax.inject.Provider

private fun KtTypeElement.singleTypeArgument(): KtTypeReference {
  return children
    .filterIsInstance<KtTypeArgumentList>()
    .single()
    .children
    .filterIsInstance<KtTypeProjection>()
    .single()
    .children
    .filterIsInstance<KtTypeReference>()
    .single()
}

internal fun List<KtCallableDeclaration>.mapToConstructorParameters(
  module: ModuleDescriptor
): List<ConstructorParameter> =
  map { ktCallableDeclaration ->
    ktCallableDeclaration.toConstructorParameter(module = module)
  }

private fun KtCallableDeclaration.toConstructorParameter(
  module: ModuleDescriptor
): ConstructorParameter {

  val typeElement = typeReference?.typeElement
  val typeFqName = typeElement?.fqNameOrNull(module)

  val isWrappedInProvider = typeFqName == providerFqName
  val isWrappedInLazy = typeFqName == daggerLazyFqName

  var isLazyWrappedInProvider = false

  val typeName = when {
    requireTypeReference(module).isNullable() ->
      requireTypeReference(module).requireTypeName(module).copy(nullable = true)

    isWrappedInProvider || isWrappedInLazy -> {
      val typeParameterReference = typeElement!!.singleTypeArgument()

      if (isWrappedInProvider &&
        typeParameterReference.fqNameOrNull(module) == daggerLazyFqName
      ) {
        // This is a super rare case when someone injects Provider<Lazy<Type>> that requires
        // special care.
        isLazyWrappedInProvider = true
        typeParameterReference.typeElement!!.singleTypeArgument().requireTypeName(module)
      } else {
        typeParameterReference.requireTypeName(module)
      }
    }

    else -> requireTypeReference(module).requireTypeName(module)
  }.withJvmSuppressWildcardsIfNeeded(this, module)

  val assistedAnnotation = findAnnotation(assistedFqName, module)
  val assistedIdentifier =
    (assistedAnnotation?.valueArguments?.firstOrNull() as? KtValueArgument)
      ?.children
      ?.filterIsInstance<KtStringTemplateExpression>()
      ?.single()
      ?.children
      ?.first()
      ?.text
      ?: ""

  return ConstructorParameter(
    name = nameAsSafeName.asString(),
    typeName = typeName,
    providerTypeName = typeName.wrapInProvider(),
    lazyTypeName = typeName.wrapInLazy(),
    isWrappedInProvider = isWrappedInProvider,
    isWrappedInLazy = isWrappedInLazy,
    isLazyWrappedInProvider = isLazyWrappedInProvider,
    isAssisted = assistedAnnotation != null,
    assistedIdentifier = assistedIdentifier
  )
}

internal fun List<KtProperty>.mapToMemberInjectParameters(
  module: ModuleDescriptor
): List<MemberInjectParameter> = map { ktProperty ->
  ktProperty.toMemberInjectParameter(
    module = module
  )
}

private fun KtProperty.toMemberInjectParameter(
  module: ModuleDescriptor
): MemberInjectParameter {

  val constructorParameter = toConstructorParameter(module)

  val originalName = nameAsSafeName.asString()

  val containingClass = containingClass()!!
  val packageName = containingClass.containingKtFile.packageFqName.asString()

  val memberInjectorClassName = "${containingClass.generateClassName()}_MembersInjector"
  val memberInjectorClass = ClassName(packageName, memberInjectorClassName)

  val qualifierAnnotations = annotationEntries.qualifierAnnotationSpecs(module)
  val isSetterInjected = isSetterInjected(module)

  // setter delegates require a "set" prefix for their inject function
  val accessName = if (isSetterInjected) {
    "set${originalName.capitalize()}"
  } else {
    originalName
  }
  return MemberInjectParameter(
    name = constructorParameter.name,
    typeName = constructorParameter.typeName,
    providerTypeName = constructorParameter.providerTypeName,
    lazyTypeName = constructorParameter.lazyTypeName,
    isWrappedInProvider = constructorParameter.isWrappedInProvider,
    isWrappedInLazy = constructorParameter.isWrappedInLazy,
    isLazyWrappedInProvider = constructorParameter.isLazyWrappedInProvider,
    isAssisted = constructorParameter.isAssisted,
    assistedIdentifier = constructorParameter.assistedIdentifier,
    memberInjectorClassName = memberInjectorClass,
    originalName = originalName,
    isSetterInjected = isSetterInjected,
    accessName = accessName,
    qualifierAnnotationSpecs = qualifierAnnotations,
    injectedFieldSignature = requireFqName()
  )
}

fun List<KtAnnotationEntry>.qualifierAnnotationSpecs(
  module: ModuleDescriptor
): List<AnnotationSpec> = mapNotNull { annotationEntry ->
  if (!annotationEntry.isQualifier(module)) {
    null
  } else {
    annotationEntry.toAnnotationSpec(module)
  }
}

fun KtAnnotationEntry.toAnnotationSpec(
  module: ModuleDescriptor
): AnnotationSpec {

  fun KtExpression.codeBlock(): CodeBlock {

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
          if (this is KtNameReferenceExpression) {
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
            // Everything else isn't supported.
            ?: run {
              throw AnvilCompilationException(
                "Couldn't resolve FqName $ref for Psi element: $text",
                element = this
              )
            }
        }
      }
      is KtCollectionLiteralExpression -> CodeBlock.of(
        getInnerExpressions()
          .map { it.codeBlock() }
          .joinToString(prefix = "[", postfix = "]")
      )
      // primitives
      else -> CodeBlock.of(text)
    }
  }

  val fqName = requireFqName(module)

  return AnnotationSpec.builder(fqName.asClassName(module))
    .apply {
      valueArguments
        .filterIsInstance<KtValueArgument>()
        .mapNotNull { valueArgument ->
          valueArgument.getArgumentExpression()?.codeBlock()
        }.forEach {
          addMember(it)
        }
    }
    .build()
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

/**
 * Converts the parameter list to comma separated argument list that can be used to call other
 * functions, e.g.
 * ```
 * [param0: String, param1: Int] -> "param0, param1"
 * ```
 * [asProvider] allows you to decide if each parameter is wrapped in a `Provider` interface. If
 * true, then the `get()` function will be called for the provider parameter. If false, then
 * then always only the parameter name will used in the argument list:
 * ```
 * "param0.get()" vs "param0"
 * ```
 * Set [includeModule] to true if a Dagger module instance is part of the argument list.
 */
internal fun List<Parameter>.asArgumentList(
  asProvider: Boolean,
  includeModule: Boolean
): String {
  return this
    .let { list ->
      if (asProvider) {
        list.map { parameter ->
          when {
            parameter.isLazyWrappedInProvider ->
              "${ProviderOfLazy::class.qualifiedName}.create(${parameter.name})"
            parameter.isWrappedInProvider -> parameter.name
            // Normally Dagger changes Lazy<Type> parameters to a Provider<Type> (usually the
            // container is a joined type), therefore we use `.lazy(..)` to convert the Provider
            // to a Lazy. Assisted parameters behave differently and the Lazy type is not changed
            // to a Provider and we can simply use the parameter name in the argument list.
            parameter.isWrappedInLazy && parameter.isAssisted -> parameter.name
            parameter.isWrappedInLazy -> "$daggerDoubleCheckFqNameString.lazy(${parameter.name})"
            parameter.isAssisted -> parameter.name
            else -> "${parameter.name}.get()"
          }
        }
      } else list.map { it.name }
    }
    .let {
      if (includeModule) {
        val result = it.toMutableList()
        result.add(0, "module")
        result.toList()
      } else {
        it
      }
    }
    .joinToString()
}

internal fun TypeName.wrapInProvider(): ParameterizedTypeName {
  return Provider::class.asClassName().parameterizedBy(this)
}

internal fun TypeName.wrapInLazy(): ParameterizedTypeName {
  return Lazy::class.asClassName().parameterizedBy(this)
}
