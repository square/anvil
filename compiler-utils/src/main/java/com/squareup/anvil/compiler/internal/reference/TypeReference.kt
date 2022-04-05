package com.squareup.anvil.compiler.internal.reference

import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.internal.asClassName
import com.squareup.anvil.compiler.internal.classDescriptor
import com.squareup.anvil.compiler.internal.classDescriptorOrNull
import com.squareup.anvil.compiler.internal.fqNameOrNull
import com.squareup.anvil.compiler.internal.reference.TypeReference.Descriptor
import com.squareup.anvil.compiler.internal.reference.TypeReference.Psi
import com.squareup.anvil.compiler.internal.requireFqName
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.WildcardTypeName
import org.jetbrains.kotlin.builtins.isFunctionType
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFunctionType
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtProjectionKind
import org.jetbrains.kotlin.psi.KtSuperTypeListEntry
import org.jetbrains.kotlin.psi.KtTypeArgumentList
import org.jetbrains.kotlin.psi.KtTypeElement
import org.jetbrains.kotlin.psi.KtTypeProjection
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull
import org.jetbrains.kotlin.types.DefinitelyNotNullType
import org.jetbrains.kotlin.types.FlexibleType
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.Variance.INVARIANT
import org.jetbrains.kotlin.types.Variance.IN_VARIANCE
import org.jetbrains.kotlin.types.Variance.OUT_VARIANCE
import org.jetbrains.kotlin.types.isNullable
import org.jetbrains.kotlin.types.typeUtil.isTypeParameter
import kotlin.LazyThreadSafetyMode.NONE

@ExperimentalAnvilApi
public sealed class TypeReference {

  public abstract val declaringClass: ClassReference

  /**
   * If the type refers to class and is not a generic type, then the returned reference
   * is non-null.
   */
  protected abstract val classReference: ClassReference?

  protected abstract val typeNameOrNull: TypeName?
  protected abstract val typeName: TypeName

  /**
   * For `Map<String, Int>` this will return [`String`, `Int`].
   */
  public abstract val unwrappedTypes: List<TypeReference>

  public val module: AnvilModuleDescriptor get() = declaringClass.module

  public fun asClassReferenceOrNull(): ClassReference? = classReference
  public fun asClassReference(): ClassReference = classReference
    ?: throw AnvilCompilationExceptionTypReference(
      typeReference = this,
      message = "Unable to convert a type reference to a class reference."
    )

  public fun asTypeNameOrNull(): TypeName? = typeNameOrNull
  public fun asTypeName(): TypeName = typeName

  public abstract fun resolveGenericTypeOrNull(
    implementingClass: ClassReference
  ): TypeReference?

  public fun resolveGenericTypeNameOrNull(
    implementingClass: ClassReference
  ): TypeName? {
    asClassReferenceOrNull()?.let {
      // Then it's a normal type and not a generic type.
      return asTypeName()
    }

    return resolveGenericTypeOrNull(implementingClass)
      ?.asTypeNameOrNull()
      ?: asTypeNameOrNull()
  }

  public fun isGenericType(): Boolean = asClassReferenceOrNull()?.isGenericClass() ?: true
  public abstract fun isFunctionType(): Boolean
  public abstract fun isNullable(): Boolean

  override fun toString(): String {
    return "${this::class.qualifiedName}(declaringClass=$declaringClass, " +
      "classReference=$classReference)"
  }

  public class Psi internal constructor(
    public val type: KtTypeReference,
    override val declaringClass: ClassReference.Psi
  ) : TypeReference() {
    override val classReference: ClassReference? by lazy(NONE) {
      type.fqNameOrNull(module)?.toClassReference(module)
    }

    override val typeName: TypeName by lazy {
      type.requireTypeName().lambdaFix()
    }

    override val typeNameOrNull: TypeName?
      get() = try {
        typeName
      } catch (e: AnvilCompilationException) {
        // Usually the method should return a nullable value, but throwing an error gives us
        // better hints which element exactly fails to be converted to a TypeName while debugging.
        // So it's better to keep that mechanism in place and catch the exception to move on.
        null
      }

    override val unwrappedTypes: List<Psi> by lazy(NONE) {
      type.typeElement!!.children
        .filterIsInstance<KtTypeArgumentList>()
        .single()
        .children
        .filterIsInstance<KtTypeProjection>()
        .map { typeProjection ->
          typeProjection.children
            .filterIsInstance<KtTypeReference>()
            .single()
            .toTypeReference(declaringClass)
        }
    }

    override fun resolveGenericTypeOrNull(implementingClass: ClassReference): TypeReference? {
      return resolveTypeReference(implementingClass)
    }

    /**
     * Safely resolves a PSI [KtTypeReference], when that type reference may be a generic
     * expressed by a type variable name. This is done by inspecting the class hierarchy to
     * find where the generic type is declared, then resolving *that* reference.
     *
     * For instance, given:
     *
     * ```
     * interface Factory<T> {
     *   fun create(): T
     * }
     *
     * interface ServiceFactory : Factory<Service>
     * ```
     *
     * The KtTypeReference `T` will fail to resolve, since it isn't a type. This function will
     * instead look to the `ServiceFactory` interface, then look at the supertype declaration
     * in order to determine the type.
     *
     * @param implementingClass The class which actually references the type. In the above
     * example, this would be `ServiceFactory`.
     */
    internal fun resolveTypeReference(
      implementingClass: ClassReference
    ): TypeReference? {
      // If the element isn't a type variable name like `T`, it can be resolved through imports.
      type.typeElement?.fqNameOrNull(module)
        ?.let { return this }

      return resolveGenericTypeReference(implementingClass, declaringClass, type.text)
    }

    override fun isFunctionType(): Boolean = type.typeElement is KtFunctionType

    override fun isNullable(): Boolean = type.typeElement is KtNullableType

    private fun KtTypeReference.requireTypeName(): TypeName {
      fun PsiElement.fail(): Nothing = throw AnvilCompilationException(
        message = "Couldn't resolve type: $text",
        element = this
      )

      fun KtUserType.isTypeParameter(): Boolean {
        return parents.filterIsInstance<KtClassOrObject>().first().typeParameters.any {
          val typeParameter = it.text.split(":").first().trim()
          typeParameter == text
        }
      }

      fun KtUserType.findExtendsBound(): List<FqName> {
        return parents.filterIsInstance<KtClassOrObject>()
          .first()
          .typeParameters
          .mapNotNull { it.fqName }
      }

      fun KtTypeElement.requireTypeName(): TypeName {
        return when (this) {
          is KtUserType -> {
            val className = fqNameOrNull(module)?.asClassName(module)
              ?: if (isTypeParameter()) {
                val bounds = findExtendsBound().map { it.asClassName(module) }
                return TypeVariableName(text, bounds)
              } else {
                throw AnvilCompilationException("Couldn't resolve fqName.", element = this)
              }

            val typeArgumentList = typeArgumentList
            if (typeArgumentList != null) {
              className.parameterizedBy(
                typeArgumentList.arguments.map { typeProjection ->
                  if (typeProjection.projectionKind == KtProjectionKind.STAR) {
                    STAR
                  } else {
                    val typeReference = typeProjection.typeReference ?: typeProjection.fail()
                    typeReference
                      .requireTypeName()
                      .let { typeName ->
                        // Preserve annotations, e.g. List<@JvmSuppressWildcards Abc>.
                        if (typeReference.annotationEntries.isNotEmpty()) {
                          typeName.copy(
                            annotations = typeName.annotations + typeReference.annotationEntries
                              .map { annotationEntry ->
                                AnnotationSpec
                                  .builder(
                                    annotationEntry
                                      .requireFqName(module)
                                      .asClassName(module)
                                  )
                                  .build()
                              }
                          )
                        } else {
                          typeName
                        }
                      }
                      .let { typeName ->
                        val modifierList = typeProjection.modifierList
                        when {
                          modifierList == null -> typeName
                          modifierList.hasModifier(KtTokens.OUT_KEYWORD) ->
                            WildcardTypeName.producerOf(typeName)
                          modifierList.hasModifier(KtTokens.IN_KEYWORD) ->
                            WildcardTypeName.consumerOf(typeName)
                          else -> typeName
                        }
                      }
                  }
                }
              )
            } else {
              className
            }
          }
          is KtFunctionType ->
            LambdaTypeName.get(
              receiver = receiver?.typeReference?.requireTypeName(),
              parameters = parameterList
                ?.parameters
                ?.map { parameter ->
                  val parameterReference = parameter.typeReference ?: parameter.fail()
                  ParameterSpec.unnamed(parameterReference.requireTypeName())
                }
                ?: emptyList(),
              returnType = (returnTypeReference ?: fail())
                .requireTypeName()
            )
          is KtNullableType -> {
            (innerType ?: fail()).requireTypeName().copy(nullable = true)
          }
          else -> fail()
        }
      }

      return (typeElement ?: fail()).requireTypeName()
    }
  }

  public class Descriptor internal constructor(
    public val type: KotlinType,
    override val declaringClass: ClassReference.Descriptor
  ) : TypeReference() {
    override val classReference: ClassReference? by lazy(NONE) {
      type.classDescriptorOrNull()?.toClassReference(module)
    }

    override val typeNameOrNull: TypeName? by lazy(NONE) {
      type.asTypeNameOrNull()?.lambdaFix()
    }

    override val typeName: TypeName
      get() = typeNameOrNull ?: throw AnvilCompilationExceptionTypReference(
        typeReference = this,
        message = "Unable to convert the Kotlin type $type to a type name for declaring class " +
          "${declaringClass.fqName}."
      )

    override val unwrappedTypes: List<Descriptor> by lazy(NONE) {
      type.arguments.map { it.type.toTypeReference(declaringClass) }
    }

    override fun resolveGenericTypeOrNull(implementingClass: ClassReference): TypeReference? {
      return resolveGenericKotlinTypeOrNull(implementingClass)
    }

    /**
     * Safely resolves a [KotlinType], when that type reference is a generic expressed by a type
     * variable name. This is done by inspecting the class hierarchy to find where the generic
     * type is declared, then resolving *that* reference.
     *
     * For instance, given:
     *
     * ```
     * interface SomeFactory : Function1<String, SomeClass>
     * ```
     *
     * There's an invisible `fun invoke(p1: P1): R`. If `SomeFactory` is parsed using PSI (such
     * as if it's generated), then the function can only be parsed to have the projected types
     * of `P1` and `R`
     *
     * @param implementingClass The class which actually references the type. In the above example,
     * this would be `SomeFactory`.
     */
    internal fun resolveGenericKotlinTypeOrNull(
      implementingClass: ClassReference
    ): TypeReference? {
      type.classDescriptorOrNull()?.fqNameOrNull()
        ?.let { return this }

      val parameterKotlinType = when (type) {
        is FlexibleType -> {
          // A FlexibleType comes from Java code where the compiler doesn't know whether it's a
          // nullable or non-nullable type. toString() returns something like "(T..T?)". To get
          // proper results we use the type that's not nullable, "T" in this example.
          if (type.lowerBound.isMarkedNullable) {
            type.upperBound
          } else {
            type.lowerBound
          }
        }
        is DefinitelyNotNullType -> {
          // This is known to be not null in Java, such as something annotated `@NotNull` or
          // controlled by a JSR305 or jSpecify annotation.
          // This is a special type and this logic appears to match how kotlinc is handling it here
          // https://github.com/JetBrains/kotlin/blob/9ee0d6b60ac4f0ea0ccc5dd01146bab92fabcdf2/core/descriptors/src/org/jetbrains/kotlin/types/TypeUtils.java#L455-L458
          type.original
        }
        else -> type
      }

      return resolveGenericTypeReference(
        implementingClass = implementingClass,
        declaringClass = declaringClass,
        parameterName = parameterKotlinType.toString()
      )
    }

    override fun isFunctionType(): Boolean = type.isFunctionType

    override fun isNullable(): Boolean = type.isNullable()

    private fun KotlinType.asTypeNameOrNull(): TypeName? {
      if (isTypeParameter()) return TypeVariableName(toString())

      val className = classDescriptor().asClassName()
      if (arguments.isEmpty()) return className.copy(nullable = isMarkedNullable)

      val argumentTypeNames = arguments.map { typeProjection ->
        if (typeProjection.isStarProjection) {
          STAR
        } else {
          val typeName = typeProjection.type.asTypeNameOrNull() ?: return null
          when (typeProjection.projectionKind) {
            INVARIANT -> typeName
            OUT_VARIANCE -> WildcardTypeName.producerOf(typeName)
            IN_VARIANCE -> WildcardTypeName.consumerOf(typeName)
          }
        }
      }

      return className.parameterizedBy(argumentTypeNames).copy(nullable = isMarkedNullable)
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

private fun resolveGenericTypeReference(
  implementingClass: ClassReference,
  declaringClass: ClassReference,
  parameterName: String
): TypeReference? {
  // If the class/interface declaring the generic is the receiver class,
  // then the generic hasn't been set to a concrete type and can't be resolved.
  if (implementingClass == declaringClass) return null

  // Used to determine which parameter to look at in a KtTypeArgumentList.
  val indexOfType = declaringClass.indexOfTypeParameter(parameterName)

  when (implementingClass) {
    is ClassReference.Psi -> {
      // Find where the supertype is actually declared by matching the FqName of the
      // SuperTypeListEntry to the type which declares the generic we're trying to resolve.
      // After finding the SuperTypeListEntry, just take the TypeReference from its type
      // argument list.
      val resolvedTypeReference = implementingClass.superTypeListEntryOrNull(declaringClass.fqName)
        ?.typeReference
        ?.typeElement
        ?.getChildOfType<KtTypeArgumentList>()
        ?.arguments
        ?.get(indexOfType)
        ?.typeReference

      return resolvedTypeReference
        ?.containingClass()
        ?.toClassReference(implementingClass.module)
        ?.let { resolvedDeclaringClass ->
          resolvedTypeReference
            .toTypeReference(resolvedDeclaringClass)
            .resolveTypeReference(implementingClass)
        }
    }
    is ClassReference.Descriptor -> {
      val resolvedTypeReference = implementingClass
        .superTypeOrNull(declaringClass.fqName)
        ?.arguments
        ?.getOrNull(indexOfType)
        ?.type

      return resolvedTypeReference
        ?.containingClassReference(implementingClass)
        ?.let { resolvedDeclaringClass ->
          resolvedTypeReference.toTypeReference(
            resolvedDeclaringClass
          ).resolveGenericKotlinTypeOrNull(implementingClass)
        }
    }
  }
}

/**
 * Find where a super type is extended/implemented by matching the FqName of a SuperTypeListEntry to
 * the FqName of the target super type.
 *
 * For instance, given:
 *
 * ```
 * interface Base<T> {
 *   fun create(): T
 * }
 *
 * abstract class Middle : Comparable<MyClass>, Provider<Something>, Base<Something>
 *
 * class InjectClass : Middle()
 * ```
 *
 * We start at the declaration of `InjectClass`, looking for a super declaration of `Base<___>`.
 * Since `InjectClass` doesn't declare it, we look through the supers of `Middle` and find it, then
 * resolve `T` to `Something`.
 */
private fun ClassReference.Psi.superTypeListEntryOrNull(
  superTypeFqName: FqName
): KtSuperTypeListEntry? {
  return allSuperTypeClassReferences(includeSelf = true)
    .filterIsInstance<ClassReference.Psi>()
    .firstNotNullOfOrNull { classReference ->
      classReference.clazz
        .superTypeListEntries
        .firstOrNull { it.requireFqName(module) == superTypeFqName }
    }
}

private fun ClassReference.Descriptor.superTypeOrNull(
  superTypeFqName: FqName
): KotlinType? {
  return allSuperTypeClassReferences(includeSelf = true)
    .filterIsInstance<ClassReference.Descriptor>()
    .firstNotNullOfOrNull { classReference ->
      classReference.clazz
        .typeConstructor
        .supertypes
        .firstOrNull {
          it.classDescriptorOrNull()
            ?.toClassReference(module)
            ?.fqName == superTypeFqName
        }
    }
}

/**
 * This is a rough corollary to [KtElement.containingClass()].
 *
 *  ```
 *  class Parent<T>
 *  class Child : Parent<String>
 *  ```
 *  In this example, [Child] is the [implementingClass] and [this] is [T]
 *
 * @param implementingClass: The lowest descendant implementing class, e.g. Child from
 */
private fun KotlinType.containingClassReference(
  implementingClass: ClassReference
): ClassReference.Descriptor? {
  val containingClassReference = implementingClass.allSuperTypeClassReferences(true)
    .toList()
    .filterIsInstance<ClassReference.Descriptor>()
    .find {
      it.typeParameters.any { typeParameter ->
        typeParameter.name == toString()
      }
    }
  // If no containing class was found, this is likely already a concrete type and we try using it
  // directly
  return containingClassReference ?: classDescriptorOrNull()
    ?.toClassReference(implementingClass.module)
}

/**
 * Converts a lambda `TypeName` to a standard `kotlin.Function*` type.
 *
 * given the lambda type: `(kotlin.String) -> kotlin.Unit`
 * returns the function type: `kotlin.Function1<String, Unit>`
 */
private fun TypeName.lambdaFix(): TypeName {
  // This is a special case when mixing parsing between descriptors and PSI.  Descriptors
  // will always represent lambda types as kotlin.Function* types, so lambda arguments
  // must be converted or their signatures won't match.
  // see https://github.com/square/anvil/issues/400
  val lambdaTypeName = this as? LambdaTypeName ?: return this

  val allTypes = listOfNotNull(lambdaTypeName.receiver) +
    lambdaTypeName.parameters.map { it.type } +
    lambdaTypeName.returnType

  return ClassName("kotlin", "Function${allTypes.size - 1}")
    .parameterizedBy(allTypes)
    .copy(nullable = isNullable)
}
