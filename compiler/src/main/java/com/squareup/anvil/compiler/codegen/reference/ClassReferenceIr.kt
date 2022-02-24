package com.squareup.anvil.compiler.codegen.reference

import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.fqName
import com.squareup.anvil.compiler.internal.reference.Visibility
import com.squareup.anvil.compiler.internal.reference.Visibility.INTERNAL
import com.squareup.anvil.compiler.internal.reference.Visibility.PRIVATE
import com.squareup.anvil.compiler.internal.reference.Visibility.PROTECTED
import com.squareup.anvil.compiler.internal.reference.Visibility.PUBLIC
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.util.isInterface
import org.jetbrains.kotlin.name.FqName
import kotlin.LazyThreadSafetyMode.NONE

internal class ClassReferenceIr(
  val clazz: IrClassSymbol,
  val context: IrPluginContext
) : AnnotatedReferenceIr {
  val fqName: FqName = clazz.fqName

  val isInterface: Boolean = clazz.owner.isInterface

  val visibility: Visibility = parseVisibility()

  private fun parseVisibility(): Visibility {
    return when (clazz.owner.visibility) {
      DescriptorVisibilities.PUBLIC -> PUBLIC
      DescriptorVisibilities.PRIVATE -> PRIVATE
      DescriptorVisibilities.INTERNAL -> INTERNAL
      DescriptorVisibilities.PROTECTED -> PROTECTED
      else -> throw AnvilCompilationExceptionClassReferenceIr(
        this,
        "Encountered an unsupported visibility ${clazz.owner.visibility.name} for class $fqName"
      )
    }
  }

  override val annotations: List<AnnotationReferenceIr> by lazy(NONE) {
    clazz.owner.annotations.map { it.toAnnotationReference(context, this) }
  }

  override fun toString(): String {
    return "${this::class.simpleName}($fqName)"
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ClassReferenceIr) return false

    if (fqName != other.fqName) return false

    return true
  }

  override fun hashCode(): Int {
    return fqName.hashCode()
  }
}

internal fun IrClassSymbol.toClassReference(context: IrPluginContext) =
  ClassReferenceIr(this, context)

@Suppress("FunctionName")
internal fun AnvilCompilationExceptionClassReferenceIr(
  classReference: ClassReferenceIr,
  message: String,
  cause: Throwable? = null
): AnvilCompilationException = AnvilCompilationException(
  element = classReference.clazz,
  message = message,
  cause = cause
)
