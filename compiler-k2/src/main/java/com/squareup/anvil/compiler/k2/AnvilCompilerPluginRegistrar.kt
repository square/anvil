package com.squareup.anvil.compiler.k2

import com.google.auto.service.AutoService
import com.squareup.anvil.compiler.k2.ir.CanaryK2IrExtension
import com.squareup.anvil.compiler.k2.ir.GeneratedDeclarationsIrBodyFiller
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter

@AutoService(CompilerPluginRegistrar::class)
public class AnvilCompilerPluginRegistrar : CompilerPluginRegistrar() {

  override val supportsK2: Boolean get() = true

  override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
    FirExtensionRegistrarAdapter.registerExtension(AnvilFirExtensionRegistrar())

    IrGenerationExtension.registerExtension(GeneratedDeclarationsIrBodyFiller())
    IrGenerationExtension.registerExtension(CanaryK2IrExtension())
  }
}
