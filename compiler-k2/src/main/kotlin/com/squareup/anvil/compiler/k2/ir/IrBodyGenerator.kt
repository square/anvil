package com.squareup.anvil.compiler.k2.ir

import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid

internal abstract class IrBodyGenerator : IrElementVisitorVoid {

  abstract fun interestedIn(key: GeneratedDeclarationKey?): Boolean
  abstract fun generateBodyForFunction(
    function: IrSimpleFunction,
    key: GeneratedDeclarationKey?,
  ): IrBody?

  abstract fun generateBodyForConstructor(
    constructor: IrConstructor,
    key: GeneratedDeclarationKey?,
  ): IrBody?

  final override fun visitElement(element: IrElement) {
    element.acceptChildrenVoid(this)
  }

  final override fun visitSimpleFunction(declaration: IrSimpleFunction) {
    val origin = declaration.origin
    if (origin !is IrDeclarationOrigin.GeneratedByPlugin || !interestedIn(origin.pluginKey)) {
      visitElement(declaration)
      return
    }
    require(declaration.body == null)
    declaration.body = generateBodyForFunction(declaration, origin.pluginKey)
  }

  final override fun visitConstructor(declaration: IrConstructor) {
    val origin = declaration.origin
    if (origin !is IrDeclarationOrigin.GeneratedByPlugin || !interestedIn(origin.pluginKey) || declaration.body != null) {
      visitElement(declaration)
      return
    }
    declaration.body = generateBodyForConstructor(declaration, origin.pluginKey)
  }
}
