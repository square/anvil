package com.squareup.anvil.compiler.internal.reference

import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.internal.reference.VariableReference.Descriptor
import com.squareup.anvil.compiler.internal.reference.VariableReference.Psi
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallableDeclaration

public sealed interface VariableReference {

  public val fqName: FqName

  public val module: AnvilModuleDescriptor

  public val name: String

  public val setterAnnotations: List<AnnotationReference>
  public val getterAnnotations: List<AnnotationReference>

  public fun visibility(): Visibility
  public fun isLateinit(): Boolean

  public fun typeOrNull(): TypeReference?
  public fun type(): TypeReference = typeOrNull()
    ?: throw AnvilCompilationExceptionVariableReference(
      variableReference = this,
      message = "Unable to get type for property $fqName."
    )

  public sealed interface Psi : VariableReference {
    public val property: KtCallableDeclaration
  }

  public sealed interface Descriptor : VariableReference {
    public val property: PropertyDescriptor
  }
}

@ExperimentalAnvilApi
@Suppress("FunctionName")
public fun AnvilCompilationExceptionVariableReference(
  variableReference: VariableReference,
  message: String,
  cause: Throwable? = null
): AnvilCompilationException = when (variableReference) {
  is Psi -> AnvilCompilationException(
    element = variableReference.property,
    message = message,
    cause = cause
  )
  is Descriptor -> AnvilCompilationException(
    propertyDescriptor = variableReference.property,
    message = message,
    cause = cause
  )
}
