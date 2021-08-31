package com.squareup.anvil.compiler.codegen.dagger

import com.squareup.anvil.compiler.codegen.Parameter
import com.squareup.anvil.compiler.daggerDoubleCheckFqNameString
import com.squareup.anvil.compiler.injectFqName
import com.squareup.anvil.compiler.internal.capitalize
import com.squareup.anvil.compiler.internal.findAnnotation
import com.squareup.anvil.compiler.internal.generateClassName
import com.squareup.anvil.compiler.internal.hasAnnotation
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
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
//  Include superclass: https://github.com/square/anvil/issues/343
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
  packageName: String,
  clazz: KtClassOrObject,
  memberInjectParameters: List<Parameter>,
  memberInjectProperties: List<KtProperty>,
  instanceName: String,
  module: ModuleDescriptor
) {
  val memberInjectorClassName = "${clazz.generateClassName()}_MembersInjector"
  val memberInjectorClass = ClassName(packageName, memberInjectorClassName)

  memberInjectParameters.forEachIndexed { index, parameter ->
    val property = memberInjectProperties[index]
    val functionName = "inject${property.memberInjectionAccessName(module).capitalize()}"

    val param = when {
      parameter.isWrappedInProvider -> parameter.name
      parameter.isWrappedInLazy ->
        "$daggerDoubleCheckFqNameString.lazy(${parameter.name})"
      else -> parameter.name + ".get()"
    }

    addStatement("%T.$functionName($instanceName, $param)", memberInjectorClass)
  }
}

/** Returns the access name for a property, which is prefixed with "set" if it's a setter. */
internal fun KtProperty.memberInjectionAccessName(module: ModuleDescriptor): String {
  val propertyName = nameAsSafeName.asString()
  return if (isSetterInjected(module)) {
    "set${propertyName.capitalize()}"
  } else {
    propertyName
  }
}
