package com.squareup.anvil.compiler.internal.reference

import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.anvil.compiler.internal.argumentType
import com.squareup.anvil.compiler.internal.classDescriptor
import com.squareup.anvil.compiler.internal.reference.AnnotationArgumentReference.Descriptor
import com.squareup.anvil.compiler.internal.reference.AnnotationArgumentReference.Psi
import com.squareup.anvil.compiler.internal.requireFqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtClassLiteralExpression
import org.jetbrains.kotlin.psi.KtCollectionLiteralExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentName
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType
import org.jetbrains.kotlin.resolve.constants.ArrayValue
import org.jetbrains.kotlin.resolve.constants.BooleanValue
import org.jetbrains.kotlin.resolve.constants.ConstantValue
import org.jetbrains.kotlin.resolve.constants.EnumValue
import org.jetbrains.kotlin.resolve.constants.KClassValue
import org.jetbrains.kotlin.resolve.constants.StringValue
import kotlin.LazyThreadSafetyMode.NONE

@ExperimentalAnvilApi
public sealed class AnnotationArgumentReference {

  public abstract val annotation: AnnotationReference
  public abstract val name: String?
  public abstract val resolvedName: String

  public val module: AnvilModuleDescriptor get() = annotation.module

  protected abstract val value: Any

  // Maybe we need to make the return type nullable and allow for a default value.
  @Suppress("UNCHECKED_CAST")
  public fun <T : Any> value(): T = value as T

  override fun toString(): String {
    return "${AnnotationArgumentReference::class.simpleName}(name=$name, value=$value, " +
      "resolvedName=$resolvedName)"
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is AnnotationArgumentReference) return false

    if (name != other.name) return false
    if (value != other.value) return false
    if (resolvedName != other.resolvedName) return false

    return true
  }

  override fun hashCode(): Int {
    var result = name.hashCode()
    result = 31 * result + value.hashCode()
    result = 31 * result + resolvedName.hashCode()
    return result
  }

  public class Psi internal constructor(
    public val argument: KtValueArgument,
    override val annotation: AnnotationReference.Psi,
    override val name: String?,
    override val resolvedName: String
  ) : AnnotationArgumentReference() {
    protected override val value: Any by lazy(NONE) {
      fun fail(): Nothing {
        throw NotImplementedError("Don't know how to handle ${argument.children.last().text}.")
      }

      when (val psiElement = argument.children.last()) {
        is KtClassLiteralExpression -> psiElement.requireFqName(module).toClassReference(module)
        is KtCollectionLiteralExpression ->
          psiElement.children
            .filterIsInstance<KtClassLiteralExpression>()
            .map { it.requireFqName(module).toClassReference(module) }
        is KtConstantExpression -> {
          // This could be any primitive type, so we need to check.
          val parameterFqName = annotation.classReference
            .constructors
            .single()
            .parameters
            .single { it.name == resolvedName }
            .type()
            .fqName
            .asString()

          when (parameterFqName) {
            Boolean::class.qualifiedName -> psiElement.text.toBooleanStrictOrNull()
            Int::class.qualifiedName -> psiElement.text.toIntOrNull()
            Long::class.qualifiedName -> psiElement.text.toLongOrNull()
            Double::class.qualifiedName -> psiElement.text.toDoubleOrNull()
            Byte::class.qualifiedName -> psiElement.text.toByteOrNull()
            Short::class.qualifiedName -> psiElement.text.toShortOrNull()
            Float::class.qualifiedName -> psiElement.text.toFloatOrNull()
            else -> fail()
          } ?: fail()
        }
        is KtStringTemplateExpression ->
          psiElement
            .getChildOfType<KtStringTemplateEntry>()
            ?.text
            ?: fail()
        is KtNameReferenceExpression -> psiElement.text
        else -> fail()
      }
    }
  }

  public class Descriptor internal constructor(
    public val argument: ConstantValue<*>,
    override val annotation: AnnotationReference.Descriptor,
    override val name: String,
    override val resolvedName: String = name
  ) : AnnotationArgumentReference() {

    protected override val value: Any by lazy(NONE) {
      fun fail(): Nothing {
        throw NotImplementedError("Don't know how to handle $argument.")
      }

      when (argument) {
        is KClassValue -> argument.toClassReference()
        is ArrayValue -> argument.value.map { it.toClassReference() }
        is BooleanValue -> argument.value
        is StringValue -> argument.value
        is EnumValue -> argument.enumEntryName.asString()
        else -> fail()
      }
    }

    private fun ConstantValue<*>.toClassReference(): ClassReference =
      argumentType(module).classDescriptor().toClassReference(module)
  }
}

@ExperimentalAnvilApi
public fun KtValueArgument.toAnnotationArgumentReference(
  annotationReference: AnnotationReference.Psi,
  indexOfArgument: Int
): Psi {
  val children = children
  val name = (children.firstOrNull() as? KtValueArgumentName)?.asName?.asString()

  // If no name is specified, then look up the name in the annotation class.
  val resolvedName = name ?: annotationReference.classReference
    .constructors
    .single()
    .parameters[indexOfArgument]
    .name

  return Psi(
    argument = this,
    annotation = annotationReference,
    name = name,
    resolvedName = resolvedName
  )
}

@ExperimentalAnvilApi
public fun Pair<Name, ConstantValue<*>>.toAnnotationArgumentReference(
  annotationReference: AnnotationReference.Descriptor
): Descriptor {
  return Descriptor(
    argument = second,
    annotation = annotationReference,
    name = first.asString()
  )
}
