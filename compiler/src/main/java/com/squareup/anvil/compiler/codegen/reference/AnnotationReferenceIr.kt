package com.squareup.anvil.compiler.codegen.reference

import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.argument
import com.squareup.anvil.compiler.internal.reference.AnnotationReference
import com.squareup.anvil.compiler.kclassUnwrapped
import com.squareup.anvil.compiler.parentScope
import com.squareup.anvil.compiler.scope
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.name.FqName
import kotlin.LazyThreadSafetyMode.NONE

internal class AnnotationReferenceIr(
  val annotation: IrConstructorCall,
  val classReference: ClassReferenceIr,
  declaringClass: ClassReferenceIr?
) {
  val fqName: FqName
    get() = classReference.fqName

  private val context: IrPluginContext
    get() = classReference.context

  val scope: ClassReferenceIr
    get() = scopeOrNull ?: throw AnvilCompilationException(
      element = annotation,
      message = "Couldn't find scope for $fqName."
    )

  val scopeOrNull: ClassReferenceIr? by lazy(NONE) {
    context.referenceClass(annotation.scope())?.toClassReference(context)
  }

  val declaringClassOrNull: ClassReferenceIr? = declaringClass
  val declaringClass: ClassReferenceIr = declaringClassOrNull
    ?: throw AnvilCompilationExceptionAnnotationReferenceIr(
      annotationReference = this,
      message = "The declaring class was null, this means the annotation wasn't used on a class."
    )

  val parentScope: ClassReferenceIr by lazy(NONE) {
    context.referenceClass(annotation.parentScope())?.toClassReference(context)
      ?: throw AnvilCompilationException(
        element = annotation,
        message = "Couldn't find parent scope for $fqName."
      )
  }

  val excludedClasses: List<ClassReferenceIr> by lazy(NONE) {
    argumentClassArray("exclude")
  }

  val replacedClasses: List<ClassReferenceIr> by lazy(NONE) {
    argumentClassArray("replaces")
  }

  // TODO: May be worth introducing AnnotationArgumentIr here
  fun argumentClassArray(name: String): List<ClassReferenceIr> {
    return annotation.argumentClassArray(name)
  }

  private fun IrConstructorCall.argumentClassArray(
    name: String
  ): List<ClassReferenceIr> {
    val vararg = argument(name)?.second as? IrVararg ?: return emptyList()

    return vararg.elements
      .filterIsInstance<IrExpression>()
      .map { it.kclassUnwrapped.owner as IrClass }
      .map { it.symbol.toClassReference(context) }
  }

  override fun toString(): String = "@$fqName"

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is AnnotationReference) return false

    if (fqName != other.fqName) return false
    // TODO: Check arguments

    return true
  }

  override fun hashCode(): Int {
    var result = fqName.hashCode()
    // TODO: Add arguments hashcode
    result = 31 * result
    return result
  }
}

internal fun IrConstructorCall.toAnnotationReference(
  context: IrPluginContext,
  declaringClass: ClassReferenceIr?
) =
  AnnotationReferenceIr(
    annotation = this,
    classReference = this.symbol.owner.parentAsClass.symbol.toClassReference(context),
    declaringClass = declaringClass
  )

@Suppress("FunctionName")
internal fun AnvilCompilationExceptionAnnotationReferenceIr(
  annotationReference: AnnotationReferenceIr,
  message: String,
  cause: Throwable? = null
): AnvilCompilationException = AnvilCompilationException(
  element = annotationReference.annotation,
  message = message,
  cause = cause
)
