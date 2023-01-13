package com.squareup.anvil.compiler.internal.reference

import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.internal.reference.PropertyReference.Descriptor
import com.squareup.anvil.compiler.internal.reference.PropertyReference.Psi
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallableDeclaration

public sealed interface PropertyReference {

  public val fqName: FqName

  public val module: AnvilModuleDescriptor

  public val name: String

  public val setterAnnotations: List<AnnotationReference>
  public val getterAnnotations: List<AnnotationReference>

  public fun visibility(): Visibility
  public fun isLateinit(): Boolean

  public fun typeOrNull(): TypeReference?
  public fun type(): TypeReference = typeOrNull()
    ?: throw AnvilCompilationExceptionPropertyReference(
      propertyReference = this,
      message = "Unable to get type for property $fqName."
    )

  public sealed interface Psi : PropertyReference {
    public val property: KtCallableDeclaration
  }

  public sealed interface Descriptor : PropertyReference {
    public val property: PropertyDescriptor
  }
}

@ExperimentalAnvilApi
@Suppress("FunctionName")
public fun AnvilCompilationExceptionPropertyReference(
  propertyReference: PropertyReference,
  message: String,
  cause: Throwable? = null
): AnvilCompilationException = when (propertyReference) {
  is Psi -> AnvilCompilationException(
    element = propertyReference.property,
    message = message,
    cause = cause
  )
  is Descriptor -> AnvilCompilationException(
    propertyDescriptor = propertyReference.property,
    message = message,
    cause = cause
  )
}
