package com.squareup.anvil.compiler.k2.constructor.inject

import com.squareup.anvil.compiler.k2.ir.IrBodyGenerator
import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.common.lower.irBlockBody
import org.jetbrains.kotlin.ir.builders.IrBlockBodyBuilder
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.Scope
import org.jetbrains.kotlin.ir.builders.irCallOp
import org.jetbrains.kotlin.ir.builders.irDelegatingConstructorCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetObjectValue
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrMemberAccessExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrInstanceInitializerCallImpl
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.getClass
import org.jetbrains.kotlin.ir.util.companionObject
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.ir.util.properties

@OptIn(UnsafeDuringIrConstructionAPI::class)
internal class IrInjectConstructorFactory(
  private val context: IrPluginContext,
) : IrBodyGenerator() {
  override fun interestedIn(key: GeneratedDeclarationKey?): Boolean {
    return key == FirInjectConstructorFactoryGenerationExtension.Key
  }

  override fun generateBodyForConstructor(
    constructor: IrConstructor,
    key: GeneratedDeclarationKey?,
  ): IrBody? = DeclarationIrBuilder(context, constructor.symbol).irBlockBody(constructor) {
    +irDelegatingConstructorCall(constructor)
    +IrInstanceInitializerCallImpl(
      -1,
      -1,
      (constructor.parent as? IrClass)?.symbol ?: return null,
      context.irBuiltIns.unitType,
    )
  }

  override fun generateBodyForFunction(
    function: IrSimpleFunction,
    key: GeneratedDeclarationKey?,
  ): IrBody {
    return when (function.name) {
      InjectConstructorGenerationModel.createName,
      InjectConstructorGenerationModel.newInstance,
      -> createCompanionFunctionBody(function)
      InjectConstructorGenerationModel.factoryGetName -> createGetFunctionBody(function)
      else -> error("Unexpected function: $function")
    }
  }

  /*
   * Both create and newInstance methods follow the identical pattern of calling the constructor
   * of the return type and passing all arguments provided to the function to the constructor.
   */
  private fun createCompanionFunctionBody(function: IrSimpleFunction): IrBody {
    val constructedType = function.returnType as IrSimpleType
    val constructedClass = constructedType.classifier.owner as IrClass
    val constructor = constructedClass.primaryConstructor!!
    return IrBlockBodyBuilder(context, Scope(function.symbol), -1, -1).blockBody {
      +irReturn(
        irConstructorCallWithArguments(
          constructor = constructor,
          arguments = function.valueParameters.map(::createGetValueExpression),
        ),
      )
    }
  }

  private fun createGetFunctionBody(declaration: IrSimpleFunction): IrBody {
    val parentClass = declaration.parentAsClass
    val newInstance: IrSimpleFunction = parentClass.companionObject()!!
      .functions.single { it.name == InjectConstructorGenerationModel.newInstance }
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
              propertyType.getClass()!!.functions.first { it.name.asString() == "get" }

            irCallOp(
              callee = providerGetFunction.symbol,
              type = context.irBuiltIns.stringType,
              dispatchReceiver = irGetProperty(
                property = property,
                dispatchReceiver = declaration.dispatchReceiverParameter!!,
              ),
            )
          }
          .toList()
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
    arguments: List<IrMemberAccessExpression<*>>,
  ) = irCallOp(
    callee = callee.symbol,
    type = callee.returnType,
    dispatchReceiver = dispatchReceiver,
  ).apply {
    arguments.forEachIndexed { index, argument ->
      putValueArgument(index, argument)
    }
  }

  private fun irConstructorCallWithArguments(
    constructor: IrConstructor,
    arguments: List<IrExpression>,
  ): IrConstructorCallImpl {
    return IrConstructorCallImpl(
      startOffset = -1,
      endOffset = -1,
      type = constructor.returnType,
      symbol = constructor.symbol,
      typeArgumentsCount = 0,
      constructorTypeArgumentsCount = 0,
    ).apply {
      arguments.forEachIndexed { index, argument ->
        putValueArgument(index, argument)
      }
    }
  }

  private fun createGetValueExpression(
    parameter: IrValueParameter,
  ): IrExpression {
    return IrGetValueImpl(
      startOffset = parameter.startOffset,
      endOffset = parameter.endOffset,
      type = parameter.type,
      symbol = parameter.symbol,
    )
  }
}
