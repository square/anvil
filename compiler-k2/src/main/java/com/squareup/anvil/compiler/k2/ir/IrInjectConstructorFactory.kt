package com.squareup.anvil.compiler.k2.ir

import com.squareup.anvil.compiler.k2.AnvilFirInjectConstructorGenerationExtension
import com.squareup.anvil.compiler.k2.internal.Names
import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.builders.IrBlockBodyBuilder
import org.jetbrains.kotlin.ir.builders.IrSingleStatementBuilder
import org.jetbrains.kotlin.ir.builders.Scope
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
import org.jetbrains.kotlin.ir.util.companionObject
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallOp
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irGetObjectValue
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.types.getClass
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.expressions.impl.IrGetObjectValueImpl
import org.jetbrains.kotlin.ir.get

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
      type = function.returnType,
      returnTargetSymbol = function.symbol,
      value = constructorCall,
    )
    return irFactory.createBlockBody(-1, -1, listOf(returnStatement))
  }

  private fun createGetFunctionBody(getFunction: IrSimpleFunction): IrBody {
    getFunction.symbol.owner
    val parentClass = getFunction.parentAsClass
    val newInstance: IrSimpleFunction = parentClass.companionObject()!!
      .functions.single { it.name == Name.identifier("newInstance") }
    val companionObjectIrClass: IrClass = getFunction.parentAsClass.companionObject()!!

    return context.irFactory.createBlockBody(-1, -1) {
      val builder = IrBlockBodyBuilder(context, Scope(getFunction.symbol), -1, -1)
      val companionReference = builder.irGetObjectValue(
        type = companionObjectIrClass.defaultType,
        classSymbol = companionObjectIrClass.symbol,
      )
      val thisParam: IrValueParameter = parentClass.thisReceiver!!
      // val providerGetFunction = Names.dagger.provider
      val arguments = parentClass.properties
        .filter { properties ->
          parentClass.primaryConstructor
            ?.valueParameters?.any { it.name == properties.name }
            ?: false
        }
        .map { property: IrProperty ->
          val backingField = property.backingField!!
          val propertyType = backingField.type
          val providerGetFunction: IrSimpleFunction =
            propertyType.getClass()!!.functions.first { it.name.toString() == "get" }

          val thisReceiver = builder.irGet(thisParam)
          // statements += thisReceiver
            // IrSingleStatementBuilder(context, Scope(parentClass.symbol), -1, -1).irGet(thisParam)
          // val getBuilder = IrSingleStatementBuilder(context, Scope(parentClass.symbol), -1, -1)

          // statements += builder.irGetField(thisReceiver, backingField)
          // val param0Function = parentClass.getSimpleFunction("getParam0")!!
          val param0Provider = builder.irCallOp(
            callee = property.getter!!.symbol,
            type = property.getter!!.returnType,
            dispatchReceiver = thisReceiver
            //
          )
          statements += param0Provider
          builder.irCallOp(
            callee = providerGetFunction.symbol,
            type = context.irBuiltIns.stringType,
            dispatchReceiver = param0Provider//builder.irGetField(null, backingField)
              // builder.irCall(
              //   callee = property.getter!!.symbol,
                // callee = param0Function,
                // type = propertyType,
                // dispatchReceiver = builder.irGet(thisParam),
              // ),
          )
        }
      val newInstanceStatement = builder.irCallOp(
        callee = newInstance.symbol,
        type = newInstance.returnType,
        dispatchReceiver = companionReference,
      ).apply {
        arguments.forEachIndexed { index, argument ->
          putValueArgument(index, argument)
        }
      }

      statements += builder.irReturn(newInstanceStatement)
    }
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
