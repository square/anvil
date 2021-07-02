package com.squareup.anvil.compiler.codegen.dagger

import com.squareup.anvil.compiler.injectFqName
import com.squareup.anvil.compiler.internal.hasAnnotation
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifierTypeOrDefault

// TODO
//  Include methods: https://github.com/square/anvil/issues/339
//  Include property setters:https://github.com/square/anvil/issues/340
//  Include superclass: https://github.com/square/anvil/issues/343
internal fun KtClassOrObject.injectedMembers(module: ModuleDescriptor) = children
  .asSequence()
  .filterIsInstance<KtClassBody>()
  .flatMap { it.properties.asSequence() }
  .filterNot { it.visibilityModifierTypeOrDefault() == KtTokens.PRIVATE_KEYWORD }
  .filter { it.hasAnnotation(injectFqName, module) }
  .toList()
