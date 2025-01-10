package com.squareup.anvil.compiler.k2.ir

import com.squareup.anvil.compiler.k2.AnvilFactoryDelegateDeclarationGenerationExtension
import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrBody

internal class TransformerForExternalClassGenerator(context: IrPluginContext) :
  AbstractTransformerForGenerator(context, visitBodies = false) {
  override fun interestedIn(key: GeneratedDeclarationKey?): Boolean {
    return key == AnvilFactoryDelegateDeclarationGenerationExtension.Key
  }

  override fun generateBodyForFunction(
    function: IrSimpleFunction,
    key: GeneratedDeclarationKey?,
  ): IrBody? {
    return generateDefaultBodyForMaterializeFunction(function)
  }

  override fun generateBodyForConstructor(
    constructor: IrConstructor,
    key: GeneratedDeclarationKey?,
  ): IrBody? {
    return generateBodyForDefaultConstructor(constructor)
  }
}
