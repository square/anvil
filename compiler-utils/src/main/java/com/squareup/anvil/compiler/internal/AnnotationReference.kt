package com.squareup.anvil.compiler.internal

import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.internal.AnnotationReference.Descriptor
import com.squareup.anvil.compiler.internal.AnnotationReference.Psi
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClassLiteralExpression

/**
 * Used to create a common type between [KtAnnotationEntry] class references and
 * [AnnotationDescriptor] references, to streamline parsing.
 */
@ExperimentalAnvilApi
public sealed class AnnotationReference {

  public abstract val classReference: ClassReference
  public val fqName: FqName get() = classReference.fqName

  public class Psi internal constructor(
    public val annotation: KtAnnotationEntry,
    override val classReference: ClassReference
  ) : AnnotationReference()

  public class Descriptor internal constructor(
    public val annotation: AnnotationDescriptor,
    override val classReference: ClassReference
  ) : AnnotationReference()

  override fun toString(): String = "@$fqName"

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ClassReference) return false

    if (fqName != other.fqName) return false

    return true
  }

  override fun hashCode(): Int = fqName.hashCode()
}

@ExperimentalAnvilApi
public fun KtAnnotationEntry.toAnnotationReference(module: ModuleDescriptor): Psi {
  return Psi(this, requireFqName(module).toClassReference(module))
}

@ExperimentalAnvilApi
public fun AnnotationDescriptor.toAnnotationReference(): Descriptor {
  return Descriptor(this, requireClass().toClassReference())
}

@ExperimentalAnvilApi
public fun AnnotationReference.scope(
  module: ModuleDescriptor,
  parameterIndex: Int = 0
): ClassReference {
  return when (this) {
    is Psi ->
      annotation
        .findAnnotationArgument<KtClassLiteralExpression>(name = "scope", index = parameterIndex)
        ?.requireFqName(module)
        ?.toClassReference(module)
    is Descriptor -> annotation.scope(module).toClassReference()
  } ?: throw AnvilCompilationException(message = "Couldn't find scope for $fqName.")
}
