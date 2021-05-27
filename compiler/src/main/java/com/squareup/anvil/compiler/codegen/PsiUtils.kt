package com.squareup.anvil.compiler.codegen

import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.assistedInjectFqName
import com.squareup.anvil.compiler.contributesBindingFqName
import com.squareup.anvil.compiler.contributesMultibindingFqName
import com.squareup.anvil.compiler.contributesToFqName
import com.squareup.anvil.compiler.injectFqName
import com.squareup.anvil.compiler.internal.findAnnotationArgument
import com.squareup.anvil.compiler.internal.hasAnnotation
import com.squareup.anvil.compiler.internal.requireAnnotation
import com.squareup.anvil.compiler.internal.requireFqName
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClassLiteralExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtCollectionLiteralExpression
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.allConstructors

/**
 * Returns the with [injectAnnotationFqName] annotated constructor for this class.
 * [injectAnnotationFqName] must be either `@Inject` or `@AssistedInject`. If the class contains
 * multiple constructors annotated with either of these annotations, then this method throws
 * an error as multiple injected constructors aren't allowed.
 */
internal fun KtClassOrObject.injectConstructor(
  injectAnnotationFqName: FqName,
  module: ModuleDescriptor
): KtConstructor<*>? {
  if (injectAnnotationFqName != injectFqName && injectAnnotationFqName != assistedInjectFqName) {
    throw IllegalArgumentException(
      "injectAnnotationFqName must be either $injectFqName or $assistedInjectFqName. " +
        "It was $injectAnnotationFqName."
    )
  }

  val constructors = allConstructors.filter {
    it.hasAnnotation(injectFqName, module) || it.hasAnnotation(assistedInjectFqName, module)
  }

  return when (constructors.size) {
    0 -> null
    1 -> if (constructors[0].hasAnnotation(injectAnnotationFqName, module)) {
      constructors[0]
    } else {
      null
    }
    else -> throw AnvilCompilationException(
      "Types may only contain one injected constructor.",
      element = this
    )
  }
}

internal fun KtClassOrObject.boundType(
  annotationFqName: FqName,
  module: ModuleDescriptor
): FqName? {
  return requireAnnotation(annotationFqName, module)
    .findAnnotationArgument<KtClassLiteralExpression>(name = "boundType", index = 1)
    ?.requireFqName(module)
}

internal fun KtClassOrObject.replaces(
  annotationFqName: FqName,
  module: ModuleDescriptor
): List<FqName> {
  val index = when (annotationFqName) {
    contributesToFqName -> 1
    contributesBindingFqName, contributesMultibindingFqName -> 2
    else -> throw IllegalArgumentException("$annotationFqName has no replaces field.")
  }

  return requireAnnotation(annotationFqName, module)
    .findAnnotationArgument<KtCollectionLiteralExpression>(name = "replaces", index = index)
    ?.let { classCollection ->
      classCollection.children
        .filterIsInstance<KtClassLiteralExpression>()
        .map { it.requireFqName(module) }
    }
    ?: emptyList()
}
