package com.squareup.anvil.compiler.codegen

import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesBinding.Priority
import com.squareup.anvil.annotations.ContributesBinding.Priority.NORMAL
import com.squareup.anvil.compiler.contributesBindingFqName
import com.squareup.anvil.compiler.contributesMultibindingFqName
import com.squareup.anvil.compiler.internal.argumentType
import com.squareup.anvil.compiler.internal.classDescriptor
import com.squareup.anvil.compiler.internal.findAnnotationArgument
import com.squareup.anvil.compiler.internal.getAnnotationValue
import com.squareup.anvil.compiler.internal.reference.AnnotationReference
import com.squareup.anvil.compiler.internal.reference.AnnotationReference.Descriptor
import com.squareup.anvil.compiler.internal.reference.AnnotationReference.Psi
import com.squareup.anvil.compiler.internal.reference.AnvilCompilationExceptionAnnotationReference
import com.squareup.anvil.compiler.internal.reference.ClassReference
import com.squareup.anvil.compiler.internal.reference.toClassReference
import com.squareup.anvil.compiler.internal.requireFqName
import com.squareup.anvil.compiler.internal.toFqNames
import org.jetbrains.kotlin.psi.KtClassLiteralExpression
import org.jetbrains.kotlin.psi.KtCollectionLiteralExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.resolve.constants.ArrayValue
import org.jetbrains.kotlin.resolve.constants.BooleanValue
import org.jetbrains.kotlin.resolve.constants.EnumValue
import org.jetbrains.kotlin.resolve.constants.KClassValue

internal fun AnnotationReference.parentScope(): ClassReference {
  return when (this) {
    is Psi ->
      annotation
        .findAnnotationArgument<KtClassLiteralExpression>(
          name = "parentScope",
          index = 1
        )
        ?.requireFqName(module)
        ?.toClassReference(module)

    is Descriptor ->
      (annotation.getAnnotationValue("parentScope") as? KClassValue)
        ?.argumentType(module)
        ?.classDescriptor()
        ?.toClassReference(module)
  } ?: throw AnvilCompilationExceptionAnnotationReference(
    message = "Couldn't find parentScope for $fqName.",
    annotationReference = this
  )
}

internal fun AnnotationReference.modules(): List<ClassReference> {
  return when (this) {
    is Psi ->
      annotation
        .findAnnotationArgument<KtCollectionLiteralExpression>("modules", 2)
        ?.toFqNames(module)
        ?.map { it.toClassReference(module) }
        .orEmpty()
    is Descriptor ->
      (annotation.getAnnotationValue("modules") as? ArrayValue)
        ?.value
        ?.map { it.argumentType(module).classDescriptor().toClassReference(module) }
        .orEmpty()
  }
}

internal fun AnnotationReference.ignoreQualifier(): Boolean {
  return when (this) {
    is Psi -> {
      val index = when (fqName) {
        contributesBindingFqName -> 4
        contributesMultibindingFqName -> 3
        else -> return false
      }

      annotation
        .findAnnotationArgument<KtConstantExpression>(name = "ignoreQualifier", index = index)
        ?.text
        ?.toBooleanStrictOrNull()
        ?: false
    }

    is Descriptor -> (annotation.getAnnotationValue("ignoreQualifier") as? BooleanValue)
      ?.value
      ?: false
  }
}

internal fun AnnotationReference.priority(): Priority {
  return when (this) {
    is Descriptor -> {
      val enumValue = annotation.getAnnotationValue("priority") as? EnumValue
        ?: return NORMAL
      ContributesBinding.Priority.valueOf(enumValue.enumEntryName.asString())
    }
    is Psi -> {
      val enumValue = annotation
        .findAnnotationArgument<KtNameReferenceExpression>("priority", 4)
        ?: return NORMAL

      ContributesBinding.Priority.valueOf(enumValue.text)
    }
  }
}
