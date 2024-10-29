package com.squareup.anvil.compiler.api

import org.jetbrains.kotlin.codegen.CompilationException
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.PsiNameIdentifierOwner
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptorWithSource
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi
import org.jetbrains.kotlin.resolve.source.KotlinSourceElement
import org.jetbrains.kotlin.resolve.source.getPsi
import org.jetbrains.kotlin.util.getExceptionMessage

/**
 * `AnvilCompilationException` is thrown when Anvil specific code can't be processed or an error
 * occurs while generating code.
 */
public class AnvilCompilationException(
  message: String,
  cause: Throwable? = null,
  element: PsiElement? = null,
) : CompilationException(message, cause, element) {
  public companion object {
    public operator fun invoke(
      annotationDescriptor: AnnotationDescriptor,
      message: String,
      cause: Throwable? = null,
    ): AnvilCompilationException {
      return AnvilCompilationException(
        message = message,
        cause = cause,
        element = annotationDescriptor.identifier,
      )
    }

    public operator fun invoke(
      functionDescriptor: FunctionDescriptor,
      message: String,
      cause: Throwable? = null,
    ): AnvilCompilationException {
      return AnvilCompilationException(
        message = message,
        cause = cause,
        element = functionDescriptor.identifier,
      )
    }

    public operator fun invoke(
      parameterDescriptor: ValueParameterDescriptor,
      message: String,
      cause: Throwable? = null,
    ): AnvilCompilationException {
      return AnvilCompilationException(
        message = message,
        cause = cause,
        element = parameterDescriptor.identifier,
      )
    }

    public operator fun invoke(
      propertyDescriptor: PropertyDescriptor,
      message: String,
      cause: Throwable? = null,
    ): AnvilCompilationException {
      return AnvilCompilationException(
        message = message,
        cause = cause,
        element = propertyDescriptor.identifier,
      )
    }

    public operator fun invoke(
      classDescriptor: ClassDescriptor,
      message: String,
      cause: Throwable? = null,
    ): AnvilCompilationException {
      return AnvilCompilationException(
        message = message,
        cause = cause,
        element = classDescriptor.identifier,
      )
    }

    public operator fun invoke(
      element: IrElement? = null,
      message: String,
      cause: Throwable? = null,
    ): AnvilCompilationException {
      return AnvilCompilationException(
        message = getExceptionMessage(
          subsystemName = "Anvil",
          message = message,
          cause = cause,
          location = element?.render(),
        ),
        cause = cause,
        element = element?.psi,
      ).apply {
        if (element != null) {
          withAttachment("element.kt", element.render())
        }
      }
    }

    public operator fun invoke(
      element: IrSymbol? = null,
      message: String,
      cause: Throwable? = null,
    ): AnvilCompilationException {
      @OptIn(UnsafeDuringIrConstructionAPI::class)
      return AnvilCompilationException(
        message = message,
        cause = cause,
        element = element?.owner,
      )
    }
  }
}

private val ClassDescriptor.identifier: PsiElement?
  get() = (findPsi() as? PsiNameIdentifierOwner)?.identifyingElement

private val AnnotationDescriptor.identifier: PsiElement?
  get() = (source as? KotlinSourceElement)?.psi

private val DeclarationDescriptorWithSource.identifier: PsiElement?
  get() = (source as? KotlinSourceElement)?.psi

private val IrElement.psi: PsiElement?
  get() = when (this) {
    is IrClass -> (this.source.getPsi() as? PsiNameIdentifierOwner)?.identifyingElement
    else -> null
  }
