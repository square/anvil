package com.squareup.anvil.compiler.internal.reference

import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.internal.asFunctionType
import com.squareup.anvil.compiler.internal.asTypeNameOrNull
import com.squareup.anvil.compiler.internal.classDescriptorOrNull
import com.squareup.anvil.compiler.internal.fqNameOrNull
import com.squareup.anvil.compiler.internal.reference.TypeReference.Descriptor
import com.squareup.anvil.compiler.internal.reference.TypeReference.Psi
import com.squareup.anvil.compiler.internal.requireFqName
import com.squareup.anvil.compiler.internal.requireTypeName
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.TypeName
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

  protected abstract val typeName: TypeName?

  public val module: AnvilModuleDescriptor get() = declaringClass.module

  public fun asClassReferenceOrNull(): ClassReference? = classReference
  public fun asClassReference(): ClassReference = classReference
    ?: throw AnvilCompilationExceptionTypReference(
      typeReference = this,
      message = "Unable to convert a type reference to a class reference."
    )

  public fun asTypeNameOrNull(): TypeName? = typeName
  public fun asTypeName(): TypeName = typeName
    ?: throw AnvilCompilationExceptionTypReference(
      typeReference = this,
      message = "Unable to convert a type reference to a type reference."
    )

  // Keep this internal, because we need help from other classes for the descriptor
  // implementation, e.g. FunctionReference resolves the return type or ParameterReference.
  internal abstract fun resolveGenericTypeOrNull(
    implementingClass: ClassReference.Psi
  ): ClassReference?

  internal abstract fun resolveGenericTypeNameOrNull(
    implementingClass: ClassReference.Psi
  ): TypeName?

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

    override val typeName: TypeName? by lazy(NONE) {
      try {
        type.requireTypeName(module)
      } catch (e: AnvilCompilationException) {
        // The goal is to inline the function above and then stop throwing an exception.
        null
      }
    }

    override fun resolveGenericTypeOrNull(implementingClass: ClassReference.Psi): ClassReference? {
      classReference?.let { return it }

      val typeReference = implementingClass.resolveTypeReference(type) ?: type
      return typeReference.fqNameOrNull(module)?.toClassReference(module)
    }

    override fun resolveGenericTypeNameOrNull(implementingClass: ClassReference.Psi): TypeName? {
      val typeReference = implementingClass.resolveTypeReference(type) ?: type
      return try {
        typeReference.requireTypeName(module)
          .let { typeName ->
            // This is a special case when mixing parsing between descriptors and PSI.  Descriptors
            // will always represent lambda types as kotlin.Function* types, so lambda arguments
            // must be converted or their signatures won't match.
            // see https://github.com/square/anvil/issues/400
            (typeName as? LambdaTypeName)?.asFunctionType() ?: typeName
          }
      } catch (e: AnvilCompilationException) {
        // The goal is to inline the function above and then stop throwing an exception.
        null
      } ?: typeName
    }
  }

  public class Descriptor internal constructor(
    public val type: KotlinType,
    override val declaringClass: ClassReference.Descriptor
  ) : TypeReference() {
    override val classReference: ClassReference? by lazy(NONE) {
      type.classDescriptorOrNull()?.toClassReference(module)
    }

    override val typeName: TypeName? by lazy(NONE) {
      type.asTypeNameOrNull()
    }

    override fun resolveGenericTypeOrNull(implementingClass: ClassReference.Psi): ClassReference? {
      classReference?.let { return it }

      return implementingClass
        .resolveGenericKotlinTypeOrNull(declaringClass, type)
        ?.fqNameOrNull(module)
        ?.toClassReferenceOrNull(module)
    }

    override fun resolveGenericTypeNameOrNull(implementingClass: ClassReference.Psi): TypeName? {
      classReference?.let {
        // Then it's a normal type and not a generic type.
        return asTypeName()
      }

      return implementingClass.resolveGenericKotlinTypeOrNull(declaringClass, type)
        ?.requireTypeName(module)
        ?: typeName
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
