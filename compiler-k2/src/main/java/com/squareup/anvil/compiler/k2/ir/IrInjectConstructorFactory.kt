package com.squareup.anvil.compiler.k2.ir

import com.squareup.anvil.compiler.k2.AnvilFirInjectConstructorGenerationExtension
import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.common.lower.irBlockBody
import org.jetbrains.kotlin.ir.builders.IrBlockBodyBuilder
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.IrGeneratorWithScope
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
import org.jetbrains.kotlin.ir.builders.irCallOp
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetObjectValue
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.types.getClass
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.expressions.IrMemberAccessExpression

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

  private fun createGetFunctionBody(declaration: IrSimpleFunction): IrBody {
    val parentClass = declaration.parentAsClass
    val newInstance: IrSimpleFunction = parentClass.companionObject()!!
      .functions.single { it.name == Name.identifier("newInstance") }
    val companionObjectIrClass: IrClass = declaration.parentAsClass.companionObject()!!
    return DeclarationIrBuilder(context, declaration.symbol).run {
      irBlockBody(declaration) {
        val companionReference = irGetObjectValue(
          type = companionObjectIrClass.defaultType,
          classSymbol = companionObjectIrClass.symbol,
        )

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

            irCallOp(
              callee = providerGetFunction.symbol,
              type = context.irBuiltIns.stringType,
              dispatchReceiver = irGetProperty(
                property = property,
                dispatchReceiver = declaration.dispatchReceiverParameter!!,
              ),
            )
          }
        +irReturn(
          irCallWithArguments(
            callee = newInstance,
            dispatchReceiver = companionReference,
            arguments = arguments,
          ),
        )
      }
    }
  }

  private fun IrBuilderWithScope.irGetProperty(
    property: IrProperty,
    dispatchReceiver: IrValueParameter,
  ) = irCallOp(
    callee = property.getter!!.symbol,
    type = property.getter!!.returnType,
    dispatchReceiver = irGet(dispatchReceiver),
  )

  private fun IrBuilderWithScope.irCallWithArguments(
    callee: IrSimpleFunction,
    dispatchReceiver: IrExpression,
    arguments: Sequence<IrMemberAccessExpression<*>>
  ) = irCallOp(
    callee = callee.symbol,
    type = callee.returnType,
    dispatchReceiver = dispatchReceiver,
  ).apply {
    arguments.forEachIndexed { index, argument ->
      putValueArgument(index, argument)
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
