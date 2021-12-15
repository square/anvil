package com.squareup.anvil.compiler.codegen.dagger

import com.squareup.anvil.compiler.codegen.MemberInjectParameter
import com.squareup.anvil.compiler.codegen.Parameter
import com.squareup.anvil.compiler.codegen.mapToMemberInjectParameters
import com.squareup.anvil.compiler.codegen.memberInjectedPropertyDescriptors
import com.squareup.anvil.compiler.daggerDoubleCheckFqNameString
import com.squareup.anvil.compiler.injectFqName
import com.squareup.anvil.compiler.internal.ClassReference
import com.squareup.anvil.compiler.internal.ClassReference.Descriptor
import com.squareup.anvil.compiler.internal.ClassReference.Psi
import com.squareup.anvil.compiler.internal.allSuperTypeClassReferences
import com.squareup.anvil.compiler.internal.asClassName
import com.squareup.anvil.compiler.internal.capitalize
import com.squareup.anvil.compiler.internal.findAnnotation
import com.squareup.anvil.compiler.internal.hasAnnotation
import com.squareup.anvil.compiler.internal.isInterface
import com.squareup.anvil.compiler.internal.toClassReference
import com.squareup.kotlinpoet.FunSpec
import dagger.internal.ProviderOfLazy
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifierTypeOrDefault

internal fun KtProperty.isSetterInjected(module: ModuleDescriptor): Boolean {
  return setter?.hasAnnotation(injectFqName, module) == true ||
    findAnnotation(injectFqName, module)
    ?.useSiteTarget
    ?.getAnnotationUseSiteTarget() == AnnotationUseSiteTarget.PROPERTY_SETTER
}

// TODO
//  Include methods: https://github.com/square/anvil/issues/339
internal fun KtClassOrObject.injectedMembers(module: ModuleDescriptor) = children
  .asSequence()
  .filterIsInstance<KtClassBody>()
  .flatMap { it.properties }
  .filterNot { it.visibilityModifierTypeOrDefault() == KtTokens.PRIVATE_KEYWORD }
  .filter {
    it.hasAnnotation(injectFqName, module) ||
      it.isSetterInjected(module)
  }
  .toList()

internal fun FunSpec.Builder.addMemberInjection(
  memberInjectParameters: List<MemberInjectParameter>,
  instanceName: String
): FunSpec.Builder = apply {

  memberInjectParameters.forEach { parameter ->

    val functionName = "inject${parameter.accessName.capitalize()}"

    val param = when {
      parameter.isLazyWrappedInProvider ->
        "${ProviderOfLazy::class.qualifiedName}.create(${parameter.name})"
      parameter.isWrappedInProvider -> parameter.name
      parameter.isWrappedInLazy ->
        "$daggerDoubleCheckFqNameString.lazy(${parameter.name})"
      else -> parameter.name + ".get()"
    }

    addStatement("%T.$functionName($instanceName, $param)", parameter.memberInjectorClassName)
  }
}

/**
 * Returns all member-injected parameters for the receiver class *and any superclasses*.
 *
 * We use Psi whenever possible, to support generated code.
 *
 * Order is important. Dagger expects the properties of the most-upstream class to be listed first
 * in a factory's constructor.
 *
 * Given the hierarchy:
 * Impl -> Middle -> Base
 * The order of dependencies in `Impl_Factory`'s constructor should be:
 * Base -> Middle -> Impl
 *
 * @param inheritedOnly If true, only returns the injected properties declared in superclasses.
 *   If false, the injected properties of the receiver class will be included.
 */
internal fun KtClassOrObject.memberInjectParameters(
  module: ModuleDescriptor,
  inheritedOnly: Boolean = false
): List<MemberInjectParameter> {
  return toClassReference()
    .allSuperTypeClassReferences(module, includeSelf = !inheritedOnly)
    .filterNot { it.isInterface() }
    .toList()
    .foldRight(listOf()) { classReference, acc ->

      acc + classReference.declaredMemberInjectParameters(module, acc)
    }
}

/**
 * @param superParameters injected parameters from any super-classes, regardless of whether they're
 * overridden by the receiver class
 * @return the member-injected parameters for this class only, not including any super-classes
 */
private fun ClassReference.declaredMemberInjectParameters(
  module: ModuleDescriptor,
  superParameters: List<Parameter>
): List<MemberInjectParameter> {
  return when (this) {
    is Descriptor -> clazz.memberInjectedPropertyDescriptors()
      .mapToMemberInjectParameters(module, clazz.asClassName(), superParameters)
    is Psi -> clazz.injectedMembers(module)
      .mapToMemberInjectParameters(module, superParameters)
  }
}
