package com.squareup.anvil.compiler.codegen.reference

import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.internal.reference.AnnotationReference
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.getArgumentsWithIr
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.name.FqName
import kotlin.LazyThreadSafetyMode.NONE

internal class AnnotationReferenceIr(
  val annotation: IrConstructorCall,
  val classReference: ClassReferenceIr,
  declaringClass: ClassReferenceIr?,
) {
  val fqName: FqName
    get() = classReference.fqName

  val context: IrPluginContext
    get() = classReference.context

  val arguments: List<AnnotationArgumentReferenceIr> by lazy(NONE) {
    annotation.getArgumentsWithIr().map { it.toAnnotationArgumentReference(this) }
  }

  val scope: ClassReferenceIr
    get() = scopeOrNull ?: throw AnvilCompilationException(
      element = annotation,
      message = "Couldn't find scope for $fqName.",
    )

  val scopeOrNull: ClassReferenceIr? by lazy(NONE) {
    argumentOrNull("scope")?.value<ClassReferenceIr>()?.let {
      context.referenceClass(it.classId)?.toClassReference(context)
    }
  }

  val declaringClassOrNull: ClassReferenceIr? = declaringClass
  val declaringClass: ClassReferenceIr = declaringClassOrNull
    ?: throw AnvilCompilationExceptionAnnotationReferenceIr(
      annotationReference = this,
      message = "The declaring class was null, this means the annotation wasn't used on a class.",
    )

  val parentScope: ClassReferenceIr by lazy(NONE) {
    argumentOrNull("parentScope")?.value<ClassReferenceIr>()?.let {
      context.referenceClass(it.classId)?.toClassReference(context)
    } ?: throw AnvilCompilationException(
      element = annotation,
      message = "Couldn't find parent scope for $fqName.",
    )
  }

  val excludedClasses: List<ClassReferenceIr> by lazy(NONE) {
    argumentOrNull("exclude")?.value<List<ClassReferenceIr>>().orEmpty()
  }

  val replacedClasses: List<ClassReferenceIr> by lazy(NONE) {
    argumentOrNull("replaces")?.value<List<ClassReferenceIr>>().orEmpty()
  }

  fun argumentOrNull(name: String): AnnotationArgumentReferenceIr? {
    return arguments.singleOrNull { it.name == name }
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
}

@OptIn(UnsafeDuringIrConstructionAPI::class)
internal fun IrConstructorCall.toAnnotationReference(
  context: IrPluginContext,
  declaringClass: ClassReferenceIr?,
) =
  AnnotationReferenceIr(
    annotation = this,
    classReference = this.symbol.owner.parentAsClass.symbol.toClassReference(context),
    declaringClass = declaringClass,
  )

@Suppress("FunctionName")
internal fun AnvilCompilationExceptionAnnotationReferenceIr(
  annotationReference: AnnotationReferenceIr,
  message: String,
  cause: Throwable? = null,
): AnvilCompilationException = AnvilCompilationException(
  element = annotationReference.annotation,
  message = message,
  cause = cause,
)
