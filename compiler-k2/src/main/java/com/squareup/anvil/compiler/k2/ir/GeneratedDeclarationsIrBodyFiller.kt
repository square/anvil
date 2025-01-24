package com.squareup.anvil.compiler.k2.ir

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid

public class GeneratedDeclarationsIrBodyFiller : IrGenerationExtension {
  override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
    val transformers = listOf(
      TransformerForExternalClassGenerator(pluginContext),
      IrInjectConstructorFactory(pluginContext),
    )

    for (transformer in transformers) {
      moduleFragment.acceptChildrenVoid(transformer)
    }
  }
}
