package com.squareup.anvil.compiler.internal.reference

import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.internal.argumentType
import com.squareup.anvil.compiler.internal.classDescriptor
import com.squareup.anvil.compiler.internal.contributesBindingFqName
import com.squareup.anvil.compiler.internal.contributesMultibindingFqName
import com.squareup.anvil.compiler.internal.contributesSubcomponentFqName
import com.squareup.anvil.compiler.internal.contributesToFqName
import com.squareup.anvil.compiler.internal.findAnnotationArgument
import com.squareup.anvil.compiler.internal.getAnnotationValue
import com.squareup.anvil.compiler.internal.mapKeyFqName
import com.squareup.anvil.compiler.internal.qualifierFqName
import com.squareup.anvil.compiler.internal.reference.AnnotationReference.Descriptor
import com.squareup.anvil.compiler.internal.reference.AnnotationReference.Psi
import com.squareup.anvil.compiler.internal.requireClass
import com.squareup.anvil.compiler.internal.requireFqName
import com.squareup.anvil.compiler.internal.toFqNames
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClassLiteralExpression
import org.jetbrains.kotlin.psi.KtCollectionLiteralExpression
import org.jetbrains.kotlin.resolve.constants.ArrayValue
import org.jetbrains.kotlin.resolve.constants.KClassValue
import kotlin.LazyThreadSafetyMode.NONE

private const val DEFAULT_SCOPE_INDEX = 0

/**
 * Used to create a common type between [KtAnnotationEntry] class references and
 * [AnnotationDescriptor] references, to streamline parsing.
 */
@ExperimentalAnvilApi
public sealed class AnnotationReference {

  /**
   * Refers to the annotation class itself and not the annotated class.
   */
  public abstract val classReference: ClassReference

  /**
   * Refers to the class that is annotated with this annotation reference. Note that annotations
   * can be used at different places, e.g. properties, constructors, functions, etc., therefore
   * this field must be nullable.
   */
  protected abstract val declaringClass: ClassReference?

  public val fqName: FqName get() = classReference.fqName
  public val module: AnvilModuleDescriptor get() = classReference.module

  public fun declaringClassOrNull(): ClassReference? = declaringClass
  public fun declaringClass(): ClassReference = declaringClass
    ?: throw AnvilCompilationExceptionAnnotationReference(
      annotationReference = this,
      message = "The declaring class was null, this means the annotation wasn't used on a class."
    )

  public abstract fun scopeOrNull(parameterIndex: Int = DEFAULT_SCOPE_INDEX): ClassReference?
  public fun scope(parameterIndex: Int = DEFAULT_SCOPE_INDEX): ClassReference =
    scopeOrNull(parameterIndex)
      ?: throw AnvilCompilationExceptionAnnotationReference(
        annotationReference = this,
        message = "Couldn't find scope for $fqName."
      )

  public abstract fun boundTypeOrNull(): ClassReference?

  public abstract fun replaces(parameterIndex: Int = replacesIndex(fqName)): List<ClassReference>

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
    override val classReference: ClassReference,
    override val declaringClass: ClassReference.Psi?,
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

    override fun replaces(parameterIndex: Int): List<ClassReference> {
      return annotation
        .findAnnotationArgument<KtCollectionLiteralExpression>(
          name = "replaces",
          index = parameterIndex
        )
        ?.toFqNames(module)
        ?.map { it.toClassReference(module) }
        .orEmpty()
    }
  }

  public class Descriptor internal constructor(
    public val annotation: AnnotationDescriptor,
    override val classReference: ClassReference,
    override val declaringClass: ClassReference.Descriptor?,
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

    override fun replaces(parameterIndex: Int): List<ClassReference> {
      val replacesValue = annotation.getAnnotationValue("replaces") as? ArrayValue
      return replacesValue
        ?.value
        ?.map { it.argumentType(module).classDescriptor().toClassReference(module) }
        .orEmpty()
    }
  }
}

@ExperimentalAnvilApi
public fun KtAnnotationEntry.toAnnotationReference(
  declaringClass: ClassReference.Psi?,
  module: ModuleDescriptor
): Psi {
  return Psi(
    annotation = this,
    classReference = requireFqName(module).toClassReference(module),
    declaringClass = declaringClass
  )
}

@ExperimentalAnvilApi
public fun AnnotationDescriptor.toAnnotationReference(
  declaringClass: ClassReference.Descriptor?,
  module: ModuleDescriptor
): Descriptor {
  return Descriptor(
    annotation = this,
    classReference = requireClass().toClassReference(module),
    declaringClass = declaringClass
  )
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

private fun replacesIndex(annotationFqName: FqName): Int {
  return when (annotationFqName) {
    contributesToFqName -> 1
    contributesBindingFqName, contributesMultibindingFqName -> 2
    contributesSubcomponentFqName -> 4
    else -> throw NotImplementedError(
      "Couldn't find index of replaces argument for $annotationFqName."
    )
  }
}
