package com.squareup.anvil.compiler.codegen.dagger

import com.squareup.anvil.compiler.codegen.MemberInjectParameter
import com.squareup.anvil.compiler.codegen.mapToMemberInjectParameters
import com.squareup.anvil.compiler.codegen.superClassMemberInjectedParameters
import com.squareup.anvil.compiler.daggerDoubleCheckFqNameString
import com.squareup.anvil.compiler.injectFqName
import com.squareup.anvil.compiler.internal.allPsiSuperClasses
import com.squareup.anvil.compiler.internal.capitalize
import com.squareup.anvil.compiler.internal.classDescriptorOrNull
import com.squareup.anvil.compiler.internal.findAnnotation
import com.squareup.anvil.compiler.internal.hasAnnotation
import com.squareup.kotlinpoet.FunSpec
import dagger.internal.ProviderOfLazy
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifierTypeOrDefault
import org.jetbrains.kotlin.utils.addToStdlib.cast

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
 */
internal fun KtClassOrObject.memberInjectParameters(
  module: ModuleDescriptor
): List<MemberInjectParameter> {

  val psiSuperClasses = allPsiSuperClasses(module)

  // We start at the bottom of the hierarchy and work our way up.
  val descriptorParams = psiSuperClasses.lastOrNull()
    ?.classDescriptorOrNull(module)
    ?.superClassMemberInjectedParameters(module)
    .orEmpty()

  // Everything PSI can tell us about super-class member-injected properties.
  val allSuperclassParams = psiSuperClasses
    .reversed()
    .fold(descriptorParams) { acc, ktClassOrObject ->

      acc + ktClassOrObject.injectedMembers(module)
        .mapToMemberInjectParameters(module)
    }

  // Finally, the class itself.
  val thisClassMemberInjectedParameters = injectedMembers(module)
    .mapToMemberInjectParameters(module)

  return allSuperclassParams.plus(thisClassMemberInjectedParameters).cast()
}
