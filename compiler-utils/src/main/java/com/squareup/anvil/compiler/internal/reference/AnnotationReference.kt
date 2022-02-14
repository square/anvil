package com.squareup.anvil.compiler.internal.reference

import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.internal.argumentType
import com.squareup.anvil.compiler.internal.classDescriptor
import com.squareup.anvil.compiler.internal.findAnnotationArgument
import com.squareup.anvil.compiler.internal.getAnnotationValue
import com.squareup.anvil.compiler.internal.mapKeyFqName
import com.squareup.anvil.compiler.internal.qualifierFqName
import com.squareup.anvil.compiler.internal.reference.AnnotationReference.Descriptor
import com.squareup.anvil.compiler.internal.reference.AnnotationReference.Psi
import com.squareup.anvil.compiler.internal.requireClass
import com.squareup.anvil.compiler.internal.requireFqName
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClassLiteralExpression
import org.jetbrains.kotlin.resolve.constants.KClassValue
import kotlin.LazyThreadSafetyMode.NONE

private const val DEFAULT_SCOPE_INDEX = 0

/**
 * Used to create a common type between [KtAnnotationEntry] class references and
 * [AnnotationDescriptor] references, to streamline parsing.
 */
@ExperimentalAnvilApi
public sealed class AnnotationReference {

  public abstract val classReference: ClassReference
  public val fqName: FqName get() = classReference.fqName
  public val module: AnvilModuleDescriptor get() = classReference.module

  public abstract fun scopeOrNull(parameterIndex: Int = DEFAULT_SCOPE_INDEX): ClassReference?
  public fun scope(parameterIndex: Int = DEFAULT_SCOPE_INDEX): ClassReference =
    scopeOrNull(parameterIndex)
      ?: throw AnvilCompilationExceptionAnnotationReference(
        annotationReference = this,
        message = "Couldn't find scope for $fqName."
      )

  public abstract fun boundTypeOrNull(): ClassReference?

  public fun isQualifier(): Boolean = classReference.isAnnotatedWith(qualifierFqName)
  public fun isMapKey(): Boolean = classReference.isAnnotatedWith(mapKeyFqName)

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
  ) : AnnotationReference() {
    private val scope by lazy(NONE) { computeScope(DEFAULT_SCOPE_INDEX) }

    // We need the scope so often that it's better to cache the value. Since the index could be
    // potentially different, only cache the value for the default index.
    override fun scopeOrNull(parameterIndex: Int): ClassReference? =
      if (parameterIndex == DEFAULT_SCOPE_INDEX) scope else computeScope(parameterIndex)

    private fun computeScope(parameterIndex: Int): ClassReference? =
      annotation
        .findAnnotationArgument<KtClassLiteralExpression>(name = "scope", index = parameterIndex)
        ?.requireFqName(module)
        ?.toClassReference(module)

    override fun boundTypeOrNull(): ClassReference? {
      return annotation.findAnnotationArgument<KtClassLiteralExpression>(
        name = "boundType",
        index = 1
      )?.requireFqName(module)?.toClassReference(module)
    }
  }

  public class Descriptor internal constructor(
    public val annotation: AnnotationDescriptor,
    override val classReference: ClassReference
  ) : AnnotationReference() {
    private val scope by lazy(NONE) {
      val annotationValue = annotation.getAnnotationValue("scope") as? KClassValue
      annotationValue?.argumentType(module)?.classDescriptor()?.toClassReference(module)
    }

    override fun scopeOrNull(parameterIndex: Int): ClassReference? = scope

    override fun boundTypeOrNull(): ClassReference? {
      return (annotation.getAnnotationValue("boundType") as? KClassValue)
        ?.argumentType(module)
        ?.classDescriptor()
        ?.toClassReference(module)
    }
  }
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
@Suppress("FunctionName")
public fun AnvilCompilationExceptionAnnotationReference(
  annotationReference: AnnotationReference,
  message: String,
  cause: Throwable? = null
): AnvilCompilationException = when (annotationReference) {
  is Psi -> AnvilCompilationException(
    element = annotationReference.annotation,
    message = message,
    cause = cause
  )
  is Descriptor -> AnvilCompilationException(
    annotationDescriptor = annotationReference.annotation,
    message = message,
    cause = cause
  )
}
