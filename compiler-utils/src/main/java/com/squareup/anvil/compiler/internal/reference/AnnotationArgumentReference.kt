package com.squareup.anvil.compiler.internal.reference

import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.anvil.compiler.internal.argumentType
import com.squareup.anvil.compiler.internal.classDescriptor
import com.squareup.anvil.compiler.internal.descendant
import com.squareup.anvil.compiler.internal.fqNameOrNull
import com.squareup.anvil.compiler.internal.getContributedPropertyOrNull
import com.squareup.anvil.compiler.internal.ktFile
import com.squareup.anvil.compiler.internal.reference.AnnotationArgumentReference.Descriptor
import com.squareup.anvil.compiler.internal.reference.AnnotationArgumentReference.Psi
import com.squareup.anvil.compiler.internal.requireFqName
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtBlockStringTemplateEntry
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassLiteralExpression
import org.jetbrains.kotlin.psi.KtCollectionLiteralExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtPrefixExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtSimpleNameStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentName
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.getChildrenOfType
import org.jetbrains.kotlin.resolve.constants.ArrayValue
import org.jetbrains.kotlin.resolve.constants.BooleanValue
import org.jetbrains.kotlin.resolve.constants.ByteValue
import org.jetbrains.kotlin.resolve.constants.ConstantValue
import org.jetbrains.kotlin.resolve.constants.DoubleValue
import org.jetbrains.kotlin.resolve.constants.EnumValue
import org.jetbrains.kotlin.resolve.constants.FloatValue
import org.jetbrains.kotlin.resolve.constants.IntValue
import org.jetbrains.kotlin.resolve.constants.KClassValue
import org.jetbrains.kotlin.resolve.constants.LongValue
import org.jetbrains.kotlin.resolve.constants.ShortValue
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

  protected fun parameterFqName(): String {
    return annotation.classReference
      .constructors
      .single()
      .parameters
      .single { it.name == resolvedName }
      .type()
      .asClassReference()
      .fqName
      .asString()
  }

  protected fun List<Any>.convertToArrayIfNeeded(): Any {
    return when (parameterFqName()) {
      BooleanArray::class.qualifiedName ->
        filterIsInstance<Boolean>().toBooleanArray()
      IntArray::class.qualifiedName ->
        filterIsInstance<Int>().toIntArray()
      LongArray::class.qualifiedName ->
        filterIsInstance<Long>().toLongArray()
      DoubleArray::class.qualifiedName ->
        filterIsInstance<Double>().toDoubleArray()
      ByteArray::class.qualifiedName ->
        filterIsInstance<Byte>().toByteArray()
      ShortArray::class.qualifiedName ->
        filterIsInstance<Short>().toShortArray()
      FloatArray::class.qualifiedName ->
        filterIsInstance<Float>().toFloatArray()
      else -> this
    }
  }

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
    override val resolvedName: String,
  ) : AnnotationArgumentReference() {
    protected override val value: Any by lazy(NONE) {
      fun fail(): Nothing {
        throw NotImplementedError("Don't know how to handle ${argument.children.last().text}.")
      }

      fun List<KtProperty>.findConstPropertyWithName(name: String): KtProperty? {
        return singleOrNull { property ->
          property.hasModifier(KtTokens.CONST_KEYWORD) && property.name == name
        }
      }

      fun PsiElement.findConstantDefinitionInScope(name: String): String? {
        when (this) {
          is KtProperty, is KtNamedFunction -> {
            // Function or Property, traverse up
            return (this as KtElement).containingClass()?.findConstantDefinitionInScope(name)
              ?: containingFile.findConstantDefinitionInScope(name)
          }
          is KtObjectDeclaration -> {
            toClassReference(module)
              .declaredMemberProperties
              .mapNotNull { it.property as? KtProperty }
              .findConstPropertyWithName(name)
              ?.initializer
              ?.text
              ?.let { return it }

            if (isCompanion()) {
              // Nowhere else to look and don't try to traverse up because this is only looked at from a
              // class already.
              return null
            }
          }
          is KtFile -> {
            children.filterIsInstance<KtProperty>()
              .findConstPropertyWithName(name)
              ?.initializer
              ?.text
              ?.let { return it }
          }
          is KtClass -> {
            // Look in companion object or traverse up
            return companionObjects.asSequence()
              .mapNotNull { it.findConstantDefinitionInScope(name) }
              .firstOrNull()
              ?: containingClass()?.findConstantDefinitionInScope(name)
              ?: containingKtFile.findConstantDefinitionInScope(name)
          }
        }

        return parent?.findConstantDefinitionInScope(name)
      }

      fun parsePrimitiveType(text: String): Any {
        // We need to check the parameter for what type exactly.
        return when (parameterFqName()) {
          Boolean::class.qualifiedName, BooleanArray::class.qualifiedName ->
            text.toBooleanStrictOrNull()
          Int::class.qualifiedName, IntArray::class.qualifiedName ->
            text.toIntOrNull()
          Long::class.qualifiedName, LongArray::class.qualifiedName ->
            text.toLongOrNull()
          Double::class.qualifiedName, DoubleArray::class.qualifiedName ->
            text.toDoubleOrNull()
          Byte::class.qualifiedName, ByteArray::class.qualifiedName ->
            text.toByteOrNull()
          Short::class.qualifiedName, ShortArray::class.qualifiedName ->
            text.toShortOrNull()
          Float::class.qualifiedName, FloatArray::class.qualifiedName ->
            text.toFloatOrNull()
          String::class.qualifiedName -> {
            if (text.startsWith("\"") && text.endsWith("\"")) {
              text.substring(1, text.length - 1)
            } else {
              text
            }
          }
          else -> fail()
        } ?: fail()
      }

      fun resolvePrimitiveConstant(fqName: FqName): Any? {
        // If this constant is coming from a companion object, then we'll find it this way.
        module.resolvePropertyReferenceOrNull(fqName)
          // Prefer descriptor types for this since the parsing is already done.
          // We won't be able to resolve a descriptor
          // if the reference was also generated in this round,
          // but Anvil itself doesn't generate consts and then use them as annotation arguments.
          ?.let { it.toDescriptorOrNull() ?: it }
          ?.let { property ->
            return when (property) {
              is PropertyReference.Descriptor ->
                property.property.compileTimeInitializer?.value
              is PropertyReference.Psi ->
                // A PropertyReference.property may also be a KtParameter if it's in a constructor,
                // but if we're here we're in an object, so the property must be a KtProperty.
                (property.property as KtProperty).initializer?.let { parsePrimitiveType(it.text) }
            }
          }

        // Is the constant a top-level constant?
        return fqName.getContributedPropertyOrNull(module)
          ?.compileTimeInitializer
          ?.value
      }

      fun resolveConstant(fqName: FqName): Any? {
        return if (fqName.toClassReferenceOrNull(module) != null) {
          // That's an enum constant.
          fqName
        } else {
          // That's hopefully a constant for a primitive type.
          resolvePrimitiveConstant(fqName)
        }
      }

      fun resolveConstant(psiElement: PsiElement): Any? {
        val fqName = psiElement.fqNameOrNull(module)
          ?: (psiElement as? KtDotQualifiedExpression)
            // That's necessary for constants like kotlin.Int.MAX_VALUE, where the real FqName is
            // actually Kotlin.Int.Companion.MAX_VALUE.
            ?.firstChild
            ?.fqNameOrNull(module)
            ?.descendant(psiElement.children.last().text)
          ?: return psiElement.ktFile().importDirectives
            // Check if there's a wildcard import for the constant.
            .filter { it.isAllUnder }
            .mapNotNull { it.importPath?.fqName?.descendant(psiElement.text) }
            .firstNotNullOfOrNull { resolveConstant(it) }

        return resolveConstant(fqName)
      }

      fun parsePsiElement(psiElement: PsiElement): Any {
        return when (psiElement) {
          is KtClassLiteralExpression -> psiElement.requireFqName(module).toClassReference(module)
          is KtCollectionLiteralExpression ->
            psiElement.children.map { parsePsiElement(it) }.convertToArrayIfNeeded()
          is KtConstantExpression, is KtPrefixExpression -> parsePrimitiveType(psiElement.text)
          is KtStringTemplateExpression -> {
            val parts = psiElement.getChildrenOfType<KtStringTemplateEntry>()

            parts.map { templateEntry ->
              if (templateEntry is KtSimpleNameStringTemplateEntry ||
                templateEntry is KtBlockStringTemplateEntry
              ) {
                parsePsiElement(templateEntry.expression as PsiElement)
              } else {
                templateEntry.text
              }
            }.joinToString("").ifEmpty { fail() }
          }
          is KtNameReferenceExpression -> {
            // Those are likely enum values or another primitive constant.
            resolveConstant(psiElement)?.let { return it }

            // Check if it's a constant. Note that we don't return the constant FqName directly,
            // but return the actual value from the constant instead. That's also what the
            // descriptor APIs do. They never see the constant itself, but the
            // resolved / inlined value instead.
            psiElement
              .findConstantDefinitionInScope(psiElement.getReferencedName())
              ?.let { return parsePrimitiveType(it) }

            // If we reach here, it's a top-level constant defined in the same package but
            // a different file. In this case, we can just do the same in our generated code
            // because we're in the same package and can play by the same rules.
            return psiElement.containingKtFile.packageFqName
              .descendant(psiElement.getReferencedName())
          }
          is KtDotQualifiedExpression -> {
            // Maybe it's a fully qualified name, so check this too.
            return resolveConstant(psiElement)
              ?: resolveConstant(FqName(psiElement.text))
              ?: fail()
          }
          else -> fail()
        }
      }

      parsePsiElement(argument.children.last())
    }
  }

  public class Descriptor internal constructor(
    public val argument: ConstantValue<*>,
    override val annotation: AnnotationReference.Descriptor,
    override val name: String,
    override val resolvedName: String = name,
  ) : AnnotationArgumentReference() {

    protected override val value: Any by lazy(NONE) {
      fun fail(): Nothing {
        throw NotImplementedError("Don't know how to handle $argument.")
      }

      fun parseConstantValue(value: ConstantValue<*>): Any {
        return when (value) {
          is KClassValue -> value.toClassReference()
          is ArrayValue -> value.value.map { parseConstantValue(it) }.convertToArrayIfNeeded()
          is StringValue -> value.value
          is EnumValue ->
            value.enumClassId.asSingleFqName()
              .descendant(value.enumEntryName.asString())
          is BooleanValue -> value.value
          is IntValue -> value.value
          is LongValue -> value.value
          is DoubleValue -> value.value
          is ByteValue -> value.value
          is ShortValue -> value.value
          is FloatValue -> value.value
          else -> fail()
        }
      }

      parseConstantValue(argument)
    }

    private fun ConstantValue<*>.toClassReference(): ClassReference =
      argumentType(module).classDescriptor().toClassReference(module)
  }
}

@ExperimentalAnvilApi
public fun KtValueArgument.toAnnotationArgumentReference(
  annotationReference: AnnotationReference.Psi,
  indexOfArgument: Int,
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
    resolvedName = resolvedName,
  )
}

@ExperimentalAnvilApi
public fun Pair<Name, ConstantValue<*>>.toAnnotationArgumentReference(
  annotationReference: AnnotationReference.Descriptor,
): Descriptor {
  return Descriptor(
    argument = second,
    annotation = annotationReference,
    name = first.asString(),
  )
}
