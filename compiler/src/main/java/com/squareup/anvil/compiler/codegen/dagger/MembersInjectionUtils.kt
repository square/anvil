package com.squareup.anvil.compiler.codegen.dagger

import com.squareup.anvil.compiler.codegen.Parameter
import com.squareup.anvil.compiler.daggerDoubleCheckFqNameString
import com.squareup.anvil.compiler.injectFqName
import com.squareup.anvil.compiler.internal.capitalize
import com.squareup.anvil.compiler.internal.generateClassName
import com.squareup.anvil.compiler.internal.hasAnnotation
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifierTypeOrDefault

// TODO
//  Include methods: https://github.com/square/anvil/issues/339
//  Include superclass: https://github.com/square/anvil/issues/343
internal fun KtClassOrObject.injectedMembers(module: ModuleDescriptor) = children
  .asSequence()
  .filterIsInstance<KtClassBody>()
  .flatMap { it.properties.asSequence() }
  .filterNot { it.visibilityModifierTypeOrDefault() == KtTokens.PRIVATE_KEYWORD }
  .filter {
    it.hasAnnotation(injectFqName, module) ||
      it.setter?.hasAnnotation(injectFqName, module) == true
  }
  .toList()

internal fun FunSpec.Builder.addMemberInjection(
  packageName: String,
  clazz: KtClassOrObject,
  memberInjectParameters: List<Parameter>,
  memberInjectProperties: List<KtProperty>,
  instanceName: String
) {
  val memberInjectorClassName = "${clazz.generateClassName()}_MembersInjector"
  val memberInjectorClass = ClassName(packageName, memberInjectorClassName)

  memberInjectParameters.forEachIndexed { index, parameter ->
    val property = memberInjectProperties[index]
    val propertyName = property.nameAsSafeName.asString()
    val functionName = "inject${propertyName.capitalize()}"

    val param = when {
      parameter.isWrappedInProvider -> parameter.name
      parameter.isWrappedInLazy ->
        "$daggerDoubleCheckFqNameString.lazy(${parameter.name})"
      else -> parameter.name + ".get()"
    }

    addStatement("%T.$functionName($instanceName, $param)", memberInjectorClass)
  }
}
