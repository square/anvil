package com.squareup.anvil.compiler.internal.reference

import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.kotlinpoet.MemberName
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallableDeclaration

public sealed interface PropertyReference {

  public val fqName: FqName

  public val module: AnvilModuleDescriptor

  public val name: String
  public val memberName: MemberName

  public val setterAnnotations: List<AnnotationReference>
  public val getterAnnotations: List<AnnotationReference>

  public fun visibility(): Visibility
  public fun isLateinit(): Boolean

  public fun typeOrNull(): TypeReference?
  public fun type(): TypeReference = typeOrNull()
    ?: throw AnvilCompilationExceptionPropertyReference(
      propertyReference = this,
      message = "Unable to get type for property $fqName.",
    )

  public sealed interface Psi : PropertyReference {
    public val property: KtCallableDeclaration
  }

  public sealed interface Descriptor : PropertyReference {
    public val property: PropertyDescriptor
  }
}

internal fun ClassReference.Psi.toDescriptorReferenceOrNull(): ClassReference.Descriptor? {
  return module.resolveFqNameOrNull(fqName)?.toClassReference(module)
}

internal fun PropertyReference.toDescriptorOrNull(): PropertyReference.Descriptor? {
  return when (this) {
    is MemberPropertyReference -> toDescriptorOrNull()
    is TopLevelPropertyReference -> toDescriptorOrNull()
  }
}

@ExperimentalAnvilApi
@Suppress("FunctionName")
public fun AnvilCompilationExceptionPropertyReference(
  propertyReference: PropertyReference,
  message: String,
  cause: Throwable? = null,
): AnvilCompilationException = when (propertyReference) {
  is PropertyReference.Psi -> AnvilCompilationException(
    element = propertyReference.property,
    message = message,
    cause = cause,
  )
  is PropertyReference.Descriptor -> AnvilCompilationException(
    propertyDescriptor = propertyReference.property,
    message = message,
    cause = cause,
  )
}
