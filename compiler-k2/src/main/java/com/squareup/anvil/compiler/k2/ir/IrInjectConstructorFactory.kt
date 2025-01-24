package com.squareup.anvil.compiler.k2.ir

import com.squareup.anvil.compiler.k2.AnvilFirInjectConstructorGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.createBlockBody
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.expressions.IrConstKind
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrReturnImpl


internal class IrInjectConstructorFactory(
  context: IrPluginContext
) : AbstractTransformerForGenerator(context, visitBodies = true) {
  override fun interestedIn(key: GeneratedDeclarationKey?): Boolean {
    return key == AnvilFirInjectConstructorGenerationExtension.Key
  }

  override fun generateBodyForFunction(
    function: IrSimpleFunction, key: GeneratedDeclarationKey?
  ): IrBody {
    val const = IrConstImpl(-1, -1, irBuiltIns.intType, IrConstKind.Int, value = 10)
    val returnStatement = IrReturnImpl(-1, -1, irBuiltIns.nothingType, function.symbol, const)
    return irFactory.createBlockBody(-1, -1, listOf(returnStatement))
  }

  override fun generateBodyForConstructor(
    constructor: IrConstructor, key: GeneratedDeclarationKey?
  ): IrBody? {
    return generateBodyForDefaultConstructor(constructor)
  }
}
