package com.squareup.anvil.compiler.codegen

import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesBinding.Priority.NORMAL
import com.squareup.anvil.compiler.contributesBindingFqName
import com.squareup.anvil.compiler.contributesMultibindingFqName
import com.squareup.anvil.compiler.internal.findAnnotationArgument
import com.squareup.anvil.compiler.internal.requireAnnotation
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

internal fun KtClassOrObject.ignoreQualifier(
  module: ModuleDescriptor,
  annotationFqName: FqName
): Boolean {
  val index = when (annotationFqName) {
    contributesBindingFqName -> 4
    contributesMultibindingFqName -> 3
    else -> return false
  }

  return requireAnnotation(annotationFqName, module)
    .findAnnotationArgument<KtConstantExpression>(name = "ignoreQualifier", index = index)
    ?.text
    ?.toBooleanStrictOrNull()
    ?: false
}

/**
 * @return the priority specified in the annotation, or [NORMAL] if there is no argument.
 */
internal fun KtAnnotationEntry.priority(): ContributesBinding.Priority {
  val enumValue = findAnnotationArgument<KtNameReferenceExpression>("priority", 4) ?: return NORMAL
  return ContributesBinding.Priority.valueOf(enumValue.text)
}
