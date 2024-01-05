package com.squareup.anvil.compiler

import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.codegen.reference.ClassReferenceIr
import com.squareup.anvil.compiler.codegen.reference.toClassReference
import com.squareup.anvil.compiler.internal.classIdBestGuess
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationWithName
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrClassifierSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.classifierOrNull
import org.jetbrains.kotlin.ir.types.typeOrNull
import org.jetbrains.kotlin.ir.util.classId
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.isAnonymousObject
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

internal fun IrPluginContext.requireReferenceClass(fqName: FqName): IrClassSymbol {
  return referenceClass(fqName.classIdBestGuess()) ?: throw AnvilCompilationException(
    message = "Couldn't resolve reference for $fqName",
  )
}

internal fun ClassId.irClass(context: IrPluginContext): IrClassSymbol =
  context.requireReferenceClass(asSingleFqName())

internal fun ClassId.referenceClassOrNull(context: IrPluginContext): ClassReferenceIr? =
  context.referenceClass(asSingleFqName().classIdBestGuess())?.toClassReference(context)

internal fun IrClass.requireClassId(): ClassId {
  return classId ?: throw AnvilCompilationException(
    element = this,
    message = "Couldn't find a ClassId for $fqName.",
  )
}

@OptIn(UnsafeDuringIrConstructionAPI::class)
internal fun IrClassSymbol.requireClassId(): ClassId = owner.requireClassId()

internal val IrExpression.kclassUnwrapped: IrClassifierSymbol
  get() = (type as? IrSimpleType)?.arguments?.get(0)?.typeOrNull?.classifierOrNull
    ?: throw AnvilCompilationException(
      message = "Couldn't resolve wrapped class.",
      element = this,
    )

internal val IrDeclarationWithName.fqName: FqName
  get() = fqNameWhenAvailable ?: throw AnvilCompilationException(
    message = "Couldn't find FqName for $name",
    element = this,
  )

@OptIn(UnsafeDuringIrConstructionAPI::class)
internal val IrClassSymbol.fqName: FqName get() = owner.fqName

// If we're evaluating an anonymous inner class, it cannot merge anything and will cause
// a failure if we try to resolve its [ClassId]
internal fun IrClass.shouldIgnore(): Boolean {
  return classId == null || isAnonymousObject
}
