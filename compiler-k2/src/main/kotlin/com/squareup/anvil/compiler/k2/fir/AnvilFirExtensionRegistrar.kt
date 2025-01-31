package com.squareup.anvil.compiler.k2.fir

import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import java.util.ServiceLoader

public class AnvilFirExtensionRegistrar(
  private val messageCollector: MessageCollector,
) : FirExtensionRegistrar() {

  override fun ExtensionRegistrarContext.configurePlugin() {

    val context = AnvilFirContext(messageCollector)

    val factories = ServiceLoader.load(
      AnvilFirExtensionFactory::class.java,
      javaClass.classLoader,
    )

    for (factory in factories) {

      when (factory) {
        is AnvilFirDeclarationGenerationExtension.Factory -> factory.create(context).unaryPlus()
        is AnvilFirSupertypeGenerationExtension.Factory -> factory.create(context).unaryPlus()
        is AnvilFirExtensionSessionComponent.Factory -> factory.create(context).unaryPlus()
      }
    }
  }
}
