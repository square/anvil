package com.squareup.anvil.compiler.k2

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid

public class CanaryIrMerger : IrGenerationExtension {
  @OptIn(UnsafeDuringIrConstructionAPI::class)
  override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {

    // val testComponent = pluginContext
    //   .referenceClass(Names.testComponent.classId())
    //   ?: error("TestComponent not found")

    // val componentRef = testComponent.toClassReference(pluginContext)

    moduleFragment.transform(
      object : IrElementTransformerVoid() {
        override fun visitClass(declaration: IrClass): IrStatement {

          return super.visitClass(declaration)
        }
      },
      null,
    )
  }
}
