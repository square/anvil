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

  public val module: AnvilModuleDescriptor get() = annotation.module

  protected abstract val value: Any

  // Maybe we need to make the return type nullable and allow for a default value.
  @Suppress("UNCHECKED_CAST")
  public fun <T : Any> value(): T = value as T

  public class Psi internal constructor(
    public val argument: KtValueArgument,
    override val annotation: AnnotationReference.Psi,
    override val name: String?,
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
        is KtConstantExpression -> psiElement.text.toBooleanStrictOrNull() ?: fail()
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

  public class Descriptor(
    public val argument: ConstantValue<*>,
    override val annotation: AnnotationReference.Descriptor,
    override val name: String
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
  annotationReference: AnnotationReference.Psi
): Psi {
  val children = children
  val name = (children.firstOrNull() as? KtValueArgumentName)?.asName?.asString()

  return Psi(this, annotationReference, name)
}

@ExperimentalAnvilApi
public fun Pair<Name, ConstantValue<*>>.toAnnotationArgumentReference(
  annotationReference: AnnotationReference.Descriptor
): Descriptor {
  return Descriptor(this.second, annotationReference, this.first.asString())
}
