package com.squareup.anvil.compiler.k2.ir

import com.squareup.anvil.compiler.k2.AnvilFirInjectConstructorGenerationExtension
import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.jvm.mapping.IrCallableMethod
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.IrBlockBodyBuilder
import org.jetbrains.kotlin.ir.builders.IrStatementsBuilder
import org.jetbrains.kotlin.ir.builders.Scope
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.createBlockBody
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConstKind
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrReturnImpl
import org.jetbrains.kotlin.ir.expressions.impl.fromSymbolOwner
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.companionObject
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.builders.irCall
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
      Name.identifier("create"), Name.identifier("newInstance") -> createFunctionBody(function)
      Name.identifier("get") -> createGetFunctionBody(function)
      else -> error("Unexpected function: $function")
    }
  }

  override fun generateBodyForConstructor(
    constructor: IrConstructor,
    key: GeneratedDeclarationKey?
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

  /*
  fun IrCallImpl(
    startOffset: Int,
    endOffset: Int,
    type: IrType,
    symbol: IrSimpleFunctionSymbol,
    typeArgumentsCount: Int = symbol.getRealOwner().typeParameters.size,
    origin: IrStatementOrigin? = null,
    superQualifierSymbol: IrClassSymbol? = null,
): IrCallImpl = IrCallImpl(
   */
  private fun createGetFunctionBody(getFunction: IrSimpleFunction): IrBody {
    val parentClass = getFunction.parentAsClass
    val newInstance: IrSimpleFunction = parentClass.companionObject()!!
      .functions.single { it.name == Name.identifier("newInstance") }

    // val constructorCall = IrConstructorCallImpl(
    //   startOffset = -1,
    //   endOffset = -1,
    //   type = getFunction.returnType,
    //   symbol = getFunction.returnType.classOrNull!!.constructors.first(),
    //   typeArgumentsCount = 0,
    //   constructorTypeArgumentsCount = 0,
    // )
    // IrCall
    // val constructorCall = IrCallImpl.Companion.fromSymbolOwner(
    //   UNDEFINED_OFFSET,
    //   -1,
    //   newInstance.symbol
    // )
    val constructorCall = IrBlockBodyBuilder(
      context = context,
      scope = Scope(parentClass.companionObject()!!.symbol),
      startOffset = -1,
      endOffset = -1
    ).irCall(newInstance)
    // irCall(
    //   context.irBuiltIns.anyClass,
    //   newInstance,
    // )
    //   val constructorCall = org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl(
    //     startOffset = -1,
    //     endOffset = -1,
    //     type = getFunction.returnType,
    //     symbol = newInstance.symbol,
    //   // 0,
    //   // origin = origin,
    //   superQualifierSymbol = parentClass.companionObject()!!.symbol,
    // )

    //   startOffset = -1,
    //   endOffset = -1,
    //   type = constructedType,
    //   symbol = newInstance.symbol,
    //   typeArgumentsCount = 0,
    //   constructorTypeArgumentsCount = 0,
    // )

    getFunction.valueParameters.forEachIndexed { index, parameter ->
      // val constString = IrConstImpl(-1, -1, irBuiltIns.intType, IrConstKind.String, value = "Hey")
      // val constInt = IrConstImpl(-1, -1, irBuiltIns.intType, IrConstKind.Int, value = 10)
      // // constructorCall.putValueArgument(index, createGetValueExpression(parameter))
      // constructorCall.putValueArgument(index, constString)
      // constructorCall.putValueArgument(index, constInt)
    }

    val returnStatement = IrReturnImpl(
      startOffset = -1,
      endOffset = -1,
      type = irBuiltIns.nothingType,
      returnTargetSymbol = getFunction.symbol,
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
