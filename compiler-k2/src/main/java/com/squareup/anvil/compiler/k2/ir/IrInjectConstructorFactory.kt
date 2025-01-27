package com.squareup.anvil.compiler.k2.ir

import com.squareup.anvil.compiler.k2.AnvilFirInjectConstructorGenerationExtension
import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.createBlockBody
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrReturnImpl
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.name.Name

@OptIn(UnsafeDuringIrConstructionAPI::class)
internal class IrInjectConstructorFactory(
  context: IrPluginContext
) : AbstractTransformerForGenerator(context, visitBodies = true) {
  override fun interestedIn(key: GeneratedDeclarationKey?): Boolean {
    return key == AnvilFirInjectConstructorGenerationExtension.Key
  }

  override fun generateBodyForFunction(
    function: IrSimpleFunction,
    key: GeneratedDeclarationKey?
  ): IrBody {
    return when (function.name) {
      Name.identifier("create") -> createFunctionBody(function)
      else -> error("Unexpected function: $function")
    }
  }

  override fun generateBodyForConstructor(
    constructor: IrConstructor, key: GeneratedDeclarationKey?
  ) = generateBodyForDefaultConstructor(constructor)

  private fun createFunctionBody(function: IrSimpleFunction): IrBody {
    val constructedType = function.returnType as IrSimpleType
    val constructedClass = constructedType.classifier.owner as IrClass
    val constructor = constructedClass.primaryConstructor!!
    val constructorCall = IrConstructorCallImpl(
      startOffset = -1,
      endOffset = -1,
      type = constructedType,
      symbol = constructor.symbol,
      typeArgumentsCount = 0,
      constructorTypeArgumentsCount = 0,
    )
    function.valueParameters.forEachIndexed { index, parameter ->
      constructorCall.putValueArgument(index, createGetValueExpression(parameter))
    }

    val returnStatement = IrReturnImpl(
      startOffset = -1,
      endOffset = -1,
      type = irBuiltIns.nothingType,
      returnTargetSymbol = function.symbol,
      value = constructorCall,
    )
    return irFactory.createBlockBody(-1, -1, listOf(returnStatement))
  }

  private fun createGetValueExpression(
    parameter: IrValueParameter
  ): IrExpression {
    return IrGetValueImpl(
      startOffset = parameter.startOffset,
      endOffset = parameter.endOffset,
      type = parameter.type,
      symbol = parameter.symbol,
    )
  }
}
