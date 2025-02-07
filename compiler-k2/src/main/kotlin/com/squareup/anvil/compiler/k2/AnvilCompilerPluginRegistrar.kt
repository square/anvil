package com.squareup.anvil.compiler.k2

import com.google.auto.service.AutoService
import com.squareup.anvil.compiler.k2.fir.AnvilFirExtensionRegistrar
import com.squareup.anvil.compiler.k2.ir.GeneratedDeclarationsIrBodyFiller
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.messageCollector
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter

@OptIn(ExperimentalCompilerApi::class)
@AutoService(CompilerPluginRegistrar::class)
public class AnvilCompilerPluginRegistrar : CompilerPluginRegistrar() {
  override val supportsK2: Boolean
    get() = true

  override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {

    val messageCollector = configuration.messageCollector

    FirExtensionRegistrarAdapter.Companion.registerExtension(
      AnvilFirExtensionRegistrar(messageCollector),
    )
    IrGenerationExtension.Companion.registerExtension(GeneratedDeclarationsIrBodyFiller())
  }
}
