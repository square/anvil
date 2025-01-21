@file:OptIn(UnsafeDuringIrConstructionAPI::class)

package com.squareup.anvil.compiler.k2.ir

import com.squareup.anvil.compiler.k2.AnvilFactoryDelegateDeclarationGenerationExtension
import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.createBlockBody
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.expressions.impl.IrReturnImpl
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.classOrFail
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.name.SpecialNames

internal class TransformerForExternalClassGenerator(context: IrPluginContext) :
  AbstractTransformerForGenerator(context, visitBodies = false) {
  override fun interestedIn(key: GeneratedDeclarationKey?): Boolean {
    return key == AnvilFactoryDelegateDeclarationGenerationExtension.Key
  }

  override fun generateBodyForFunction(
    function: IrSimpleFunction,
    key: GeneratedDeclarationKey?,
  ): IrBody? {
    if (function.name == SpecialNames.INIT) return null
    // if (function.name != Name.identifier("newInstance")) {
    //   return generateNewInstanceFunctionBody(function)
    // }
    return generateDefaultBodyForMaterializeFunction(function)
  }

  override fun generateBodyForConstructor(
    constructor: IrConstructor,
    key: GeneratedDeclarationKey?,
  ): IrBody? {
    return generateBodyForDefaultConstructor(constructor)
  }

  private fun generateNewInstanceFunctionBody(function: IrSimpleFunction): IrBody {
    // irFactory.createExpressionBody()
    val constructorCall = org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl(
      startOffset = -1,
      endOffset = -1,
      type = function.returnType,
      symbol = function.returnType.classOrFail.constructors.single(),
      typeArgumentsCount = 0,
      constructorTypeArgumentsCount = 0,
    )
    val returnStatement =
      IrReturnImpl(-1, -1, irBuiltIns.nothingType, function.symbol, constructorCall)
    return irFactory.createBlockBody(-1, -1, listOf(returnStatement))
  }
}
