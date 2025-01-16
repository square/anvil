package com.squareup.anvil.compiler.k2.ir

import com.squareup.anvil.compiler.k2.TopLevelDeclarationsGenerator
import com.squareup.anvil.compiler.k2.internal.Names.foo
import com.squareup.anvil.compiler.k2.internal.tree.IrTreePrinter.Companion.printEverything
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin.GeneratedByPlugin
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.createBlockBody
import org.jetbrains.kotlin.ir.expressions.IrConstKind
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrReturnImpl
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.isClassWithFqName
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid

public class CanaryK2IrExtension : IrGenerationExtension {

  override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {

    // val testComponent = pluginContext
    //   .referenceClass(Names.testComponent.classId())
    //   ?: error("TestComponent not found")

    // val componentRef = testComponent.toClassReference(pluginContext)

    moduleFragment.transform(
      object : IrElementTransformerVoid() {

        override fun visitSimpleFunction(declaration: IrSimpleFunction): IrStatement {
          val origin = declaration.origin

          if (origin !is GeneratedByPlugin || origin.pluginKey != TopLevelDeclarationsGenerator.Key) {
            visitElement(declaration)
            return super.visitSimpleFunction(declaration)
          } else {

            require(declaration.body == null)
            val irBuiltIns = pluginContext.irBuiltIns
            val function = declaration
            val irFactory = pluginContext.irFactory

            val className = declaration.valueParameters.single()
              .type.classFqName?.asString() ?: "<error>"
            val const = IrConstImpl(-1, -1, irBuiltIns.stringType, IrConstKind.String, className)
            val returnExpression = IrReturnImpl(
              startOffset = -1,
              endOffset = -1,
              type = irBuiltIns.nothingType,
              returnTargetSymbol = function.symbol,
              value = const,
            )
            declaration.body = irFactory.createBlockBody(-1, -1, listOf(returnExpression))
          }

          return super.visitSimpleFunction(declaration)
        }

        override fun visitClass(declaration: IrClass): IrStatement {

          declaration.printEverything()

          if (declaration.symbol.isClassWithFqName(foo.testComponent.toUnsafe())) {
            declaration.annotations.forEach { constructorCall ->
              // constructorCall.printEverything()
            }
          }

          return super.visitClass(declaration)
        }
      },
      null,
    )
  }
}
