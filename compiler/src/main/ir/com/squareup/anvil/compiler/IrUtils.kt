package com.squareup.anvil.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrAnnotationContainer
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationWithName
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrMemberAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrClassifierSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classifierOrNull
import org.jetbrains.kotlin.ir.types.getClass
import org.jetbrains.kotlin.ir.types.typeOrNull
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.ir.util.getArgumentsWithIr
import org.jetbrains.kotlin.name.FqName

internal fun IrPluginContext.requireReferenceClass(fqName: FqName): IrClassSymbol {
  return referenceClass(fqName) ?: throw AnvilCompilationException(
      message = "Couldn't resolve reference for $fqName"
  )
}

internal fun IrAnnotationContainer.annotationOrNull(
  annotationFqName: FqName,
  scope: FqName? = null
): IrConstructorCall? {
  return getAnnotation(annotationFqName)?.takeIf { annotation ->
    scope == null || annotation.scope() == scope
  }
}

internal fun IrAnnotationContainer.annotation(
  annotationFqName: FqName,
  scope: FqName? = null
): IrConstructorCall = annotationOrNull(annotationFqName, scope)
    ?: throw AnvilCompilationIrException(
        message = "Couldn't find $annotationFqName with scope $scope.",
        element = (this as? IrType)?.getClass()
    )

internal val IrExpression.kclassUnwrapped: IrClassifierSymbol
  get() = (type as? IrSimpleType)?.arguments?.get(0)?.typeOrNull?.classifierOrNull
      ?: throw AnvilCompilationIrException(
          message = "Couldn't resolve wrapped class.",
          element = this
      )

internal fun IrConstructorCall.scope(): FqName {
  val expression = argument("scope")?.second
      ?: throw AnvilCompilationIrException(
          message = "Couldn't find scope annotation.",
          element = this
      )

  val signature = expression.kclassUnwrapped.signature.asPublic()
      ?: throw AnvilCompilationIrException(
          message = "Couldn't resolve scope signature.",
          element = this
      )

  return FqName("${signature.packageFqName}.${signature.shortName}")
}

internal val IrDeclarationWithName.fqName: FqName
  get() = fqNameWhenAvailable ?: throw AnvilCompilationIrException(
      message = "Couldn't find FqName for $name",
      element = this
  )

internal val IrClassSymbol.fqName: FqName get() = owner.fqName

internal fun IrMemberAccessExpression<*>.argument(
  name: String
): Pair<IrValueParameter, IrExpression>? {
  return getArgumentsWithIr()
      .singleOrNull {
        it.first.name.asString() == name
      }
}

internal fun IrConstructorCall.argumentClassArray(
  name: String
): List<IrClass> {
  val vararg = argument(name)?.second as? IrVararg ?: return emptyList()

  return vararg.elements
      .filterIsInstance<IrExpression>()
      .map { it.kclassUnwrapped.owner as IrClass }
}

internal fun IrConstructorCall.replaces(): List<IrClass> = argumentClassArray("replaces")

internal fun IrConstructorCall.exclude(): List<IrClass> = argumentClassArray("exclude")
