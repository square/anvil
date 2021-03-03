package com.squareup.anvil.compiler

import org.jetbrains.kotlin.codegen.CompilationException
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.PsiNameIdentifierOwner
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi
import org.jetbrains.kotlin.resolve.source.KotlinSourceElement

class AnvilCompilationException(
  message: String,
  cause: Throwable? = null,
  element: PsiElement? = null
) : CompilationException(message, cause, element) {
  constructor(
    classDescriptor: ClassDescriptor,
    message: String,
    cause: Throwable? = null
  ) : this(message, cause = cause, element = classDescriptor.identifier)
  constructor(
    annotationDescriptor: AnnotationDescriptor,
    message: String,
    cause: Throwable? = null
  ) : this(message, cause = cause, element = annotationDescriptor.identifier)
}

private val ClassDescriptor.identifier: PsiElement?
  get() = (findPsi() as? PsiNameIdentifierOwner)?.identifyingElement

private val AnnotationDescriptor.identifier: PsiElement?
  get() = (source as? KotlinSourceElement)?.psi
