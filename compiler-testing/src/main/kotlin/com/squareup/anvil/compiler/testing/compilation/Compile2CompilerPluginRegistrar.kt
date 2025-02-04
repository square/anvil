package com.squareup.anvil.compiler.testing.compilation

import com.google.auto.service.AutoService
import com.squareup.anvil.compiler.k2.fir.AnvilFirContext
import com.squareup.anvil.compiler.k2.fir.AnvilFirDeclarationGenerationExtension
import com.squareup.anvil.compiler.k2.fir.AnvilFirExtensionFactory
import com.squareup.anvil.compiler.k2.fir.AnvilFirExtensionSessionComponent
import com.squareup.anvil.compiler.k2.fir.AnvilFirSupertypeGenerationExtension
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.messageCollector
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter

@AutoService(CompilerPluginRegistrar::class)
internal class Compile2CompilerPluginRegistrar : CompilerPluginRegistrar() {
  override val supportsK2: Boolean
    get() = true

  override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
    val factories: List<AnvilFirExtensionFactory<*>>? =
      threadLocalParams.get()?.firExtensionFactories

    if (!factories.isNullOrEmpty()) {
      FirExtensionRegistrarAdapter.registerExtension(
        Compile2FirExtensionRegistrar(
          messageCollector = configuration.messageCollector,
          factories = factories,
        ),
      )
    }
  }

  companion object {
    internal val threadLocalParams = ThreadLocal<Compile2RegistrarParams>()
  }

  data class Compile2RegistrarParams(
    val firExtensionFactories: List<AnvilFirExtensionFactory<*>>,
  )
}

public class Compile2FirExtensionRegistrar(
  private val messageCollector: MessageCollector,
  private val factories: List<AnvilFirExtensionFactory<*>>,
) : FirExtensionRegistrar() {

  override fun ExtensionRegistrarContext.configurePlugin() {

    val ctx = AnvilFirContext(messageCollector)

    for (factory in factories) {

      when (factory) {
        is AnvilFirDeclarationGenerationExtension.Factory -> factory.create(ctx).unaryPlus()
        is AnvilFirSupertypeGenerationExtension.Factory -> factory.create(ctx).unaryPlus()
        is AnvilFirExtensionSessionComponent.Factory -> factory.create(ctx).unaryPlus()
      }
    }
  }
}
