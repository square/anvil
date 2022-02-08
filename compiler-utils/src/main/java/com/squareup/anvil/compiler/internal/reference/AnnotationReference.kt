package com.squareup.anvil.compiler.internal.reference

import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.internal.findAnnotationArgument
import com.squareup.anvil.compiler.internal.reference.AnnotationReference.Descriptor
import com.squareup.anvil.compiler.internal.reference.AnnotationReference.Psi
import com.squareup.anvil.compiler.internal.requireClass
import com.squareup.anvil.compiler.internal.requireFqName
import com.squareup.anvil.compiler.internal.scope
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
  public val module: AnvilModuleDescriptor get() = classReference.module

  override fun toString(): String = "@$fqName"

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ClassReference) return false

    if (fqName != other.fqName) return false

    return true
  }

  override fun hashCode(): Int = fqName.hashCode()

  public class Psi internal constructor(
    public val annotation: KtAnnotationEntry,
    override val classReference: ClassReference
  ) : AnnotationReference()

  public class Descriptor internal constructor(
    public val annotation: AnnotationDescriptor,
    override val classReference: ClassReference
  ) : AnnotationReference()
}

@ExperimentalAnvilApi
public fun KtAnnotationEntry.toAnnotationReference(module: ModuleDescriptor): Psi {
  return Psi(this, requireFqName(module).toClassReference(module))
}

@ExperimentalAnvilApi
public fun AnnotationDescriptor.toAnnotationReference(module: ModuleDescriptor): Descriptor {
  return Descriptor(this, requireClass().toClassReference(module))
}

@ExperimentalAnvilApi
public fun AnnotationReference.scope(
  parameterIndex: Int = 0
): ClassReference {
  return when (this) {
    is Psi ->
      annotation
        .findAnnotationArgument<KtClassLiteralExpression>(name = "scope", index = parameterIndex)
        ?.requireFqName(module)
        ?.toClassReference(module)
    is Descriptor -> annotation.scope(module).toClassReference(module)
  } ?: throw AnvilCompilationException(message = "Couldn't find scope for $fqName.")
}
