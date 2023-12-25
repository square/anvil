package com.squareup.anvil.compiler.internal.reference

import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.internal.contributesBindingFqName
import com.squareup.anvil.compiler.internal.contributesMultibindingFqName
import com.squareup.anvil.compiler.internal.contributesSubcomponentFqName
import com.squareup.anvil.compiler.internal.contributesToFqName
import com.squareup.anvil.compiler.internal.daggerScopeFqName
import com.squareup.anvil.compiler.internal.mapKeyFqName
import com.squareup.anvil.compiler.internal.mergeComponentFqName
import com.squareup.anvil.compiler.internal.mergeInterfacesFqName
import com.squareup.anvil.compiler.internal.mergeModulesFqName
import com.squareup.anvil.compiler.internal.mergeSubcomponentFqName
import com.squareup.anvil.compiler.internal.qualifierFqName
import com.squareup.anvil.compiler.internal.reference.AnnotationReference.Descriptor
import com.squareup.anvil.compiler.internal.reference.AnnotationReference.Psi
import com.squareup.anvil.compiler.internal.requireFqName
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.MemberName
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.resolve.descriptorUtil.annotationClass
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

  public abstract val arguments: List<AnnotationArgumentReference>

  public fun declaringClassOrNull(): ClassReference? = declaringClass
  public open fun declaringClass(): ClassReference = declaringClass
    ?: throw AnvilCompilationExceptionAnnotationReference(
      annotationReference = this,
      message = "The declaring class was null, this means the annotation wasn't used on a class.",
    )

  public abstract fun scopeOrNull(parameterIndex: Int = DEFAULT_SCOPE_INDEX): ClassReference?
  public fun scope(parameterIndex: Int = DEFAULT_SCOPE_INDEX): ClassReference =
    scopeOrNull(parameterIndex)
      ?: throw AnvilCompilationExceptionAnnotationReference(
        annotationReference = this,
        message = "Couldn't find scope for $fqName.",
      )

  public fun boundTypeOrNull(): ClassReference? = argumentAt("boundType", 1)?.value()

  public fun replaces(parameterIndex: Int = replacesIndex(fqName)): List<ClassReference> =
    argumentAt("replaces", parameterIndex)?.value<List<ClassReference>>().orEmpty()

  public fun exclude(parameterIndex: Int = excludeIndex(fqName)): List<ClassReference> =
    argumentAt("exclude", parameterIndex)?.value<List<ClassReference>>().orEmpty()

  public fun isQualifier(): Boolean = classReference.isAnnotatedWith(qualifierFqName)
  public fun isMapKey(): Boolean = classReference.isAnnotatedWith(mapKeyFqName)
  public fun isDaggerScope(): Boolean = classReference.isAnnotatedWith(daggerScopeFqName)

  public fun toAnnotationSpec(): AnnotationSpec {
    return AnnotationSpec
      .builder(classReference.asClassName())
      .apply {
        arguments.forEach { argument ->
          when (val value = argument.value<Any>()) {
            is ClassReference -> addMember(
              "${argument.resolvedName} = %T::class",
              value.asClassName(),
            )
            is FqName -> {
              val clazz = value.toClassReferenceOrNull(module)
              if (clazz != null) {
                // That's an enum value!
                val enumMember = MemberName(
                  enclosingClassName = clazz.enclosingClass()!!.asClassName(),
                  simpleName = clazz.shortName,
                )
                addMember("${argument.resolvedName} = %M", enumMember)
              } else {
                addMember("${argument.resolvedName} = ${value.asString()}")
              }
            }
            is String -> addMember("${argument.resolvedName} = \"$value\"")
            is BooleanArray -> addMember(
              "${argument.resolvedName} = ${value.joinToString(prefix = "[", postfix = "]")}",
            )
            is IntArray -> addMember(
              "${argument.resolvedName} = ${value.joinToString(prefix = "[", postfix = "]")}",
            )
            is LongArray -> addMember(
              "${argument.resolvedName} = ${value.joinToString(prefix = "[", postfix = "]")}",
            )
            is DoubleArray -> addMember(
              "${argument.resolvedName} = ${value.joinToString(prefix = "[", postfix = "]")}",
            )
            is ByteArray -> addMember(
              "${argument.resolvedName} = ${value.joinToString(prefix = "[", postfix = "]")}",
            )
            is ShortArray -> addMember(
              "${argument.resolvedName} = ${value.joinToString(prefix = "[", postfix = "]")}",
            )
            is FloatArray -> addMember(
              "${argument.resolvedName} = ${value.joinToString(prefix = "[", postfix = "]")}",
            )
            is List<*> -> {
              when {
                value.isEmpty() -> addMember("${argument.resolvedName} = []")
                value[0] is ClassReference -> {
                  val classes = value.filterIsInstance<ClassReference>()
                  val template = classes.joinToString { "%T::class" }

                  addMember(
                    "${argument.resolvedName} = [$template]",
                    *classes.map { it.asClassName() }.toTypedArray(),
                  )
                }
                value[0] is FqName -> {
                  val args = value.filterIsInstance<FqName>().joinToString { it.asString() }
                  addMember("${argument.resolvedName} = [$args]")
                }
              }
            }
            // int, long, ... other primitives.
            else -> addMember("${argument.resolvedName} = $value")
          }
        }
      }
      .build()
  }

  override fun toString(): String = "@$fqName"

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is AnnotationReference) return false

    if (fqName != other.fqName) return false
    if (arguments != other.arguments) return false

    return true
  }

  override fun hashCode(): Int {
    var result = fqName.hashCode()
    result = 31 * result + arguments.hashCode()
    return result
  }

  public class Psi internal constructor(
    public val annotation: KtAnnotationEntry,
    override val classReference: ClassReference,
    override val declaringClass: ClassReference?,
  ) : AnnotationReference() {

    override val arguments: List<AnnotationArgumentReference.Psi> by lazy(NONE) {
      annotation.valueArguments
        .filterIsInstance<KtValueArgument>()
        .mapIndexed { index, argument ->
          argument.toAnnotationArgumentReference(this, index)
        }
    }

    private val defaultScope by lazy(NONE) { computeScope(DEFAULT_SCOPE_INDEX) }

    // We need the scope so often that it's better to cache the value. Since the index could be
    // potentially different, only cache the value for the default index.
    override fun scopeOrNull(parameterIndex: Int): ClassReference? =
      if (parameterIndex == DEFAULT_SCOPE_INDEX) defaultScope else computeScope(parameterIndex)

    private fun computeScope(parameterIndex: Int): ClassReference? {
      return argumentAt("scope", parameterIndex)?.value()
    }
  }

  public class Descriptor internal constructor(
    public val annotation: AnnotationDescriptor,
    override val classReference: ClassReference,
    override val declaringClass: ClassReference?,
  ) : AnnotationReference() {

    override val arguments: List<AnnotationArgumentReference.Descriptor> by lazy(NONE) {
      annotation.allValueArguments.toList().map { it.toAnnotationArgumentReference(this) }
    }

    private val scope by lazy(NONE) {
      arguments.singleOrNull { it.name == "scope" }?.value<ClassReference>()
    }

    override fun declaringClass(): ClassReference.Descriptor {
      return super.declaringClass() as ClassReference.Descriptor
    }

    override fun scopeOrNull(parameterIndex: Int): ClassReference? = scope
  }
}

@ExperimentalAnvilApi
public fun KtAnnotationEntry.toAnnotationReference(
  declaringClass: ClassReference.Psi?,
  module: ModuleDescriptor,
): Psi {
  return toAnnotationReference(
    classReference = requireFqName(module).toClassReference(module),
    declaringClass = declaringClass
  )
}

@ExperimentalAnvilApi
public fun KtAnnotationEntry.toAnnotationReference(
  declaringClass: ClassReference?,
  classReference: ClassReference
): Psi {
  return Psi(
    annotation = this,
    classReference = classReference,
    declaringClass = declaringClass
  )
}

@ExperimentalAnvilApi
public fun AnnotationDescriptor.toAnnotationReference(
  declaringClass: ClassReference.Descriptor?,
  module: ModuleDescriptor,
): Descriptor {
  val annotationClass = annotationClass ?: throw AnvilCompilationException(
    message = "Couldn't find the annotation class for $fqName",
  )
  return toAnnotationReference(declaringClass, annotationClass.toClassReference(module))
}

@ExperimentalAnvilApi
public fun AnnotationDescriptor.toAnnotationReference(
  declaringClass: ClassReference?,
  classReference: ClassReference
): Descriptor {
  return Descriptor(
    annotation = this,
    classReference = classReference,
    declaringClass = declaringClass
  )
}

@ExperimentalAnvilApi
public fun AnnotationReference.argumentAt(
  name: String,
  index: Int,
): AnnotationArgumentReference? {
  return arguments.singleOrNull { it.name == name }
    ?: arguments.elementAtOrNull(index)?.takeIf { it.name == null }
}

@ExperimentalAnvilApi
@Suppress("FunctionName")
public fun AnvilCompilationExceptionAnnotationReference(
  annotationReference: AnnotationReference,
  message: String,
  cause: Throwable? = null,
): AnvilCompilationException = when (annotationReference) {
  is Psi -> AnvilCompilationException(
    element = annotationReference.annotation,
    message = message,
    cause = cause,
  )
  is Descriptor -> AnvilCompilationException(
    annotationDescriptor = annotationReference.annotation,
    message = message,
    cause = cause,
  )
}

private fun replacesIndex(annotationFqName: FqName): Int {
  return when (annotationFqName) {
    contributesToFqName -> 1
    contributesBindingFqName, contributesMultibindingFqName -> 2
    contributesSubcomponentFqName -> 4
    else -> throw NotImplementedError(
      "Couldn't find index of replaces argument for $annotationFqName.",
    )
  }
}

private fun excludeIndex(annotationFqName: FqName): Int {
  return when (annotationFqName) {
    mergeInterfacesFqName -> 1
    mergeSubcomponentFqName -> 2
    mergeComponentFqName, mergeModulesFqName, contributesSubcomponentFqName -> 3
    else -> throw NotImplementedError(
      "Couldn't find index of exclude argument for $annotationFqName.",
    )
  }
}
