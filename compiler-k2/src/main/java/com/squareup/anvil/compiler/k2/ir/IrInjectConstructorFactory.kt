package com.squareup.anvil.compiler.k2.ir

import com.squareup.anvil.compiler.fqName
import com.squareup.anvil.compiler.k2.AnvilFirInjectConstructorGenerationExtension
import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.common.lower.irBlockBody
import org.jetbrains.kotlin.backend.jvm.mapping.IrCallableMethod
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.IrBlockBodyBuilder
import org.jetbrains.kotlin.ir.builders.IrSingleStatementBuilder
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
import org.jetbrains.kotlin.ir.builders.irCallOp
import org.jetbrains.kotlin.ir.builders.irConcat
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrValueDeclaration
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.declarations.impl.IrClassImpl
import org.jetbrains.kotlin.ir.expressions.IrPropertyReference
import org.jetbrains.kotlin.ir.expressions.IrValueAccessExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrClassReferenceImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetObjectValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrPropertyReferenceImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrValueSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrFieldSymbolImpl

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
    // val newInstCall2 = DeclarationIrBuilder(context, getFunction.symbol).irBlockBody(getFunction) {
    //   irConcat().apply {
    //     add
    //   }
    // }

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
    val companionObjectIrClass: IrClass = getFunction.parentAsClass.companionObject()!!
    // val companionReference = IrClassReferenceImpl(-1, -1, companionObjectIrClass.defaultType, companionObjectIrClass.symbol, companionObjectIrClass.defaultType)
    val companionReference =IrGetObjectValueImpl(
      startOffset = -1,
      endOffset = -1,
      type = companionObjectIrClass.defaultType,
      symbol = companionObjectIrClass.symbol
    )
    // IrPropertyReferenceImpl(-1, -1, companionObjectIrClass.defaultType, 0, companionObjectIrClass.symbol,)
    val newInstanceResult = IrSingleStatementBuilder(
      context = context,
      scope = Scope(companionObjectIrClass.symbol),
      startOffset = -1,
      endOffset = -1,
      origin = null
    ).irCallOp(
      callee = newInstance.symbol,
      type = getFunction.returnType,
      dispatchReceiver = companionReference,
      argument = null,
      origin = null
    )
      .apply {
        // addArguments()
      }
    // companionObjectIrClass.source.toIrConst()
    // val companionSignature = IdSignature.CommonSignature(
    //   packageFqName = companionObjectIrClass.packageFqName!!.asString(),
    //   declarationFqName = companionObjectIrClass.fqName.asString(),
    //   id = null,
    //   mask = 0,
    //   description = null
    // )
    // val companionObject: IrClassSymbol = context.symbolTable.referenceClass(companionSignature)
    // val expresssion: IrExpression = IrGetValueImpl(
    //   startOffset = -1,
    //   endOffset = -1,
    //   type = newInstance.returnType,
    //   symbol = IrValueSymbol(companionObject),
    // )
    // IrFieldSymbolImpl(
    //
    // )
    // IrPropertyReferenceImpl(
    //   startOffset=-1,
    //   endOffset=-1,
    //   type=companionObjectIrClass,
    //   symbol=companionObjectIrClass.symbol,
    //   typeArgumentsCount=0,
    //   field: IrFieldSymbol?,
    // getter: IrSimpleFunctionSymbol?,
    // setter: IrSimpleFunctionSymbol?,
    // origin: IrStatementOrigin? = null,
    // )
    // irConcat
    val returnStatement = IrReturnImpl(
      startOffset = -1,
      endOffset = -1,
      type = irBuiltIns.nothingType,
      returnTargetSymbol = getFunction.symbol,
      value = newInstanceResult,
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
