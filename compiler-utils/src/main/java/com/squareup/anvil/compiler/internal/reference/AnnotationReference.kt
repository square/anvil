package com.squareup.anvil.compiler.internal.reference

import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.internal.argumentType
import com.squareup.anvil.compiler.internal.asClassName
import com.squareup.anvil.compiler.internal.classDescriptor
import com.squareup.anvil.compiler.internal.contributesBindingFqName
import com.squareup.anvil.compiler.internal.contributesMultibindingFqName
import com.squareup.anvil.compiler.internal.contributesSubcomponentFqName
import com.squareup.anvil.compiler.internal.contributesToFqName
import com.squareup.anvil.compiler.internal.daggerScopeFqName
import com.squareup.anvil.compiler.internal.findAnnotationArgument
import com.squareup.anvil.compiler.internal.getAnnotationValue
import com.squareup.anvil.compiler.internal.mapKeyFqName
import com.squareup.anvil.compiler.internal.mergeComponentFqName
import com.squareup.anvil.compiler.internal.mergeInterfacesFqName
import com.squareup.anvil.compiler.internal.mergeModulesFqName
import com.squareup.anvil.compiler.internal.mergeSubcomponentFqName
import com.squareup.anvil.compiler.internal.qualifierFqName
import com.squareup.anvil.compiler.internal.reference.AnnotationReference.Descriptor
import com.squareup.anvil.compiler.internal.reference.AnnotationReference.Psi
import com.squareup.anvil.compiler.internal.requireClass
import com.squareup.anvil.compiler.internal.requireFqName
import com.squareup.anvil.compiler.internal.toFqNames
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.MemberName
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClassLiteralExpression
import org.jetbrains.kotlin.psi.KtCollectionLiteralExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.resolve.constants.ArrayValue
import org.jetbrains.kotlin.resolve.constants.EnumValue
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
  public val shortName: String get() = fqName.shortName().asString()
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
  public abstract fun exclude(parameterIndex: Int = excludeIndex(fqName)): List<ClassReference>

  public fun isQualifier(): Boolean = classReference.isAnnotatedWith(qualifierFqName)
  public fun isMapKey(): Boolean = classReference.isAnnotatedWith(mapKeyFqName)
  public fun isDaggerScope(): Boolean = classReference.isAnnotatedWith(daggerScopeFqName)

  public abstract fun toAnnotationSpec(): AnnotationSpec

  override fun toString(): String = "@$fqName"

  // TODO: equals() and hashcode() are wrong actually. To compare AnnotationReference instances we
  //  also need to consider the annotation values and not just the FqName. Otherwise we'll have
  //  many problems with repeatable annotations.
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

    override fun replaces(parameterIndex: Int): List<ClassReference> =
      findClassArrayAnnotationArgument("replaces", parameterIndex)

    override fun exclude(parameterIndex: Int): List<ClassReference> =
      findClassArrayAnnotationArgument("exclude", parameterIndex)

    private fun findClassArrayAnnotationArgument(
      name: String,
      parameterIndex: Int
    ): List<ClassReference> {
      return annotation
        .findAnnotationArgument<KtCollectionLiteralExpression>(name, parameterIndex)
        ?.toFqNames(module)
        ?.map { it.toClassReference(module) }
        .orEmpty()
    }

    override fun toAnnotationSpec(): AnnotationSpec {
      return AnnotationSpec.builder(classReference.asClassName())
        .apply {
          annotation.valueArguments
            .filterIsInstance<KtValueArgument>()
            .mapNotNull { valueArgument ->
              valueArgument.getArgumentExpression()?.codeBlock(module)
            }
            .forEach {
              addMember(it)
            }
        }
        .build()
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

    override fun replaces(parameterIndex: Int): List<ClassReference> =
      getClassArrayAnnotationValue("replaces")

    override fun exclude(parameterIndex: Int): List<ClassReference> =
      getClassArrayAnnotationValue("exclude")

    private fun getClassArrayAnnotationValue(name: String): List<ClassReference> {
      return (annotation.getAnnotationValue(name) as? ArrayValue)
        ?.value
        ?.map { it.argumentType(module).classDescriptor().toClassReference(module) }
        .orEmpty()
    }

    override fun toAnnotationSpec(): AnnotationSpec {
      return AnnotationSpec
        .builder(classReference.asClassName())
        .apply {
          annotation.allValueArguments.forEach { (name, value) ->
            when (value) {
              is KClassValue -> {
                val className = value.argumentType(module).classDescriptor()
                  .asClassName()
                addMember("${name.asString()} = %T::class", className)
              }
              is EnumValue -> {
                val enumMember = MemberName(
                  enclosingClassName = value.enumClassId.asSingleFqName()
                    .asClassName(module),
                  simpleName = value.enumEntryName.asString()
                )
                addMember("${name.asString()} = %M", enumMember)
              }
              // String, int, long, ... other primitives.
              else -> addMember("${name.asString()} = $value")
            }
          }
        }
        .build()
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

private fun excludeIndex(annotationFqName: FqName): Int {
  return when (annotationFqName) {
    mergeInterfacesFqName -> 1
    mergeSubcomponentFqName -> 2
    mergeComponentFqName, mergeModulesFqName, contributesSubcomponentFqName -> 3
    else -> throw NotImplementedError(
      "Couldn't find index of exclude argument for $annotationFqName."
    )
  }
}
