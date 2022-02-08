package com.squareup.anvil.compiler.codegen

import com.squareup.anvil.compiler.assistedFqName
import com.squareup.anvil.compiler.daggerLazyFqName
import com.squareup.anvil.compiler.injectFqName
import com.squareup.anvil.compiler.internal.argumentType
import com.squareup.anvil.compiler.internal.asTypeName
import com.squareup.anvil.compiler.internal.capitalize
import com.squareup.anvil.compiler.internal.classDescriptor
import com.squareup.anvil.compiler.internal.classDescriptorOrNull
import com.squareup.anvil.compiler.internal.isQualifier
import com.squareup.anvil.compiler.internal.toAnnotationSpec
import com.squareup.anvil.compiler.internal.withJvmSuppressWildcardsIfNeeded
import com.squareup.anvil.compiler.providerFqName
import com.squareup.kotlinpoet.ClassName
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor.Kind
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.isNullable
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

fun ClassDescriptor.memberInjectedPropertyDescriptors(): List<PropertyDescriptor> {

  return unsubstitutedMemberScope
    .getContributedDescriptors(DescriptorKindFilter.VARIABLES)
    .filterIsInstance<PropertyDescriptor>()
    // only return properties which are directly declared (including overrides)
    .filter { it.kind == Kind.DECLARATION }
    .filter { it.hasAnnotation(injectFqName) }
}

fun PropertyDescriptor.hasAnnotation(annotationFqName: FqName): Boolean {

  // `@Inject lateinit var` is really `@field:Inject lateinit var`, which needs `backingField`
  return backingField?.annotations?.hasAnnotation(annotationFqName) == true ||
    setter?.annotations?.hasAnnotation(annotationFqName) == true ||
    annotations.hasAnnotation(annotationFqName)
}

fun PropertyDescriptor.isSetterInjected(): Boolean {
  return setter?.annotations?.hasAnnotation(injectFqName) == true
}

fun KotlinType.fqNameOrNull(): FqName? = classDescriptorOrNull()?.fqNameOrNull()

fun KotlinType.fqName(): FqName = classDescriptor().fqNameSafe

internal fun List<CallableMemberDescriptor>.mapToMemberInjectParameters(
  module: ModuleDescriptor,
  containingClassName: ClassName,
  superParameters: List<Parameter>
): List<MemberInjectParameter> {

  return fold(listOf()) { acc, descriptor ->

    val originalName = descriptor.name.asString()

    val uniqueName = originalName.uniqueParameterName(superParameters, acc)

    val type = descriptor.safeAs<PropertyDescriptor>()?.type
      ?: descriptor.valueParameters.first().type

    val typeFqName = type.fqNameOrNull()

    val isWrappedInProvider = typeFqName == providerFqName
    val isWrappedInLazy = typeFqName == daggerLazyFqName

    var isLazyWrappedInProvider = false

    val typeName = when {
      type.isNullable() -> type.asTypeName()
        .copy(nullable = true)

      isWrappedInLazy || isWrappedInProvider -> {
        val singleTypeParamType = type.arguments
          .singleOrNull()
          ?.type

        if (isWrappedInProvider && singleTypeParamType?.fqNameOrNull() == daggerLazyFqName) {

          // This is a super rare case when someone injects Provider<Lazy<Type>> that requires
          // special care.
          isLazyWrappedInProvider = true
          singleTypeParamType.asTypeName()
        } else {
          type.argumentType().asTypeName()
        }
      }

      else -> type.asTypeName()
    }.withJvmSuppressWildcardsIfNeeded(descriptor)

    val assistedAnnotation = descriptor.annotations
      .findAnnotation(assistedFqName)

    val assistedIdentifier = assistedAnnotation
      ?.allValueArguments
      ?.values
      ?.firstOrNull()
      ?.value as? String
      ?: ""

    val memberInjectorClassName = containingClassName.simpleNames
      .joinToString("_") + "_MembersInjector"

    val memberInjectorClass = ClassName(
      containingClassName.packageName, memberInjectorClassName
    )

    val isSetterInjected = descriptor.safeAs<PropertyDescriptor>()
      ?.isSetterInjected() == true

    // setter delegates require a "set" prefix for their inject function
    val accessName = if (isSetterInjected) {
      "set${originalName.capitalize()}"
    } else {
      originalName
    }

    val qualifierAnnotations = descriptor.annotations
      .filter { it.isQualifier() }
      .map { it.toAnnotationSpec(module) }

    acc + MemberInjectParameter(
      name = uniqueName,
      originalName = originalName,
      typeName = typeName,
      providerTypeName = typeName.wrapInProvider(),
      lazyTypeName = typeName.wrapInLazy(),
      isWrappedInProvider = isWrappedInProvider,
      isWrappedInLazy = isWrappedInLazy,
      isLazyWrappedInProvider = isLazyWrappedInProvider,
      isAssisted = assistedAnnotation != null,
      assistedIdentifier = assistedIdentifier,
      memberInjectorClassName = memberInjectorClass,
      isSetterInjected = isSetterInjected,
      accessName = accessName,
      qualifierAnnotationSpecs = qualifierAnnotations,
      injectedFieldSignature = descriptor.fqNameSafe
    )
  }
}
