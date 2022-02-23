package com.squareup.anvil.compiler.internal.reference

import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.internal.classDescriptorOrNull
import com.squareup.anvil.compiler.internal.fqNameOrNull
import com.squareup.anvil.compiler.internal.reference.TypeReference.Descriptor
import com.squareup.anvil.compiler.internal.reference.TypeReference.Psi
import com.squareup.anvil.compiler.internal.requireFqName
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.types.KotlinType
import kotlin.LazyThreadSafetyMode.NONE

@ExperimentalAnvilApi
public sealed class TypeReference {

  public abstract val declaringClass: ClassReference

  /**
   * If the type refers to class and is not a generic type, then the returned reference
   * is non-null.
   */
  protected abstract val classReference: ClassReference?

  public val module: AnvilModuleDescriptor get() = declaringClass.module

  public fun asClassReferenceOrNull(): ClassReference? = classReference
  public fun asClassReference(): ClassReference = classReference
    ?: throw AnvilCompilationExceptionTypReference(
      typeReference = this,
      message = "Unable to convert a type reference to a class reference."
    )

  // Keep this internal, because we need help from other classes for the descriptor
  // implementation, e.g. FunctionReference resolves the return type or ParameterReference.
  internal abstract fun resolveGenericTypeOrNull(
    implementingClass: ClassReference.Psi
  ): ClassReference?

  override fun toString(): String {
    return "${this::class.qualifiedName}(declaringClass=$declaringClass, " +
      "classReference=$classReference)"
  }

  public class Psi internal constructor(
    public val type: KtTypeReference,
    override val declaringClass: ClassReference.Psi
  ) : TypeReference() {
    override val classReference: ClassReference? by lazy(NONE) {
      declaringClass.resolveTypeReference(type)
        ?.requireFqName(module)
        ?.toClassReference(module)
    }

    override fun resolveGenericTypeOrNull(implementingClass: ClassReference.Psi): ClassReference? {
      classReference?.let { return it }

      val typeReference = implementingClass.resolveTypeReference(type) ?: type
      return typeReference.fqNameOrNull(module)?.toClassReference(module)
    }
  }

  public class Descriptor internal constructor(
    public val type: KotlinType,
    override val declaringClass: ClassReference.Descriptor
  ) : TypeReference() {
    override val classReference: ClassReference? by lazy(NONE) {
      type.classDescriptorOrNull()?.toClassReference(module)
    }

    override fun resolveGenericTypeOrNull(implementingClass: ClassReference.Psi): ClassReference? {
      classReference?.let { return it }

      return implementingClass
        .resolveGenericKotlinTypeOrNull(declaringClass, type)
        ?.fqNameOrNull(module)
        ?.toClassReferenceOrNull(module)
    }
  }
}

@ExperimentalAnvilApi
public fun KtTypeReference.toTypeReference(declaringClass: ClassReference.Psi): Psi {
  return Psi(this, declaringClass)
}

@ExperimentalAnvilApi
public fun KotlinType.toTypeReference(declaringClass: ClassReference.Descriptor): Descriptor {
  return Descriptor(this, declaringClass)
}

@ExperimentalAnvilApi
@Suppress("FunctionName")
public fun AnvilCompilationExceptionTypReference(
  typeReference: TypeReference,
  message: String,
  cause: Throwable? = null
): AnvilCompilationException = when (typeReference) {
  is Psi -> AnvilCompilationException(
    element = typeReference.type,
    message = message,
    cause = cause
  )
  is Descriptor -> {
    AnvilCompilationException(
      classDescriptor = typeReference.declaringClass.clazz,
      message = message + " Hint: ${typeReference.type}",
      cause = cause
    )
  }
}

