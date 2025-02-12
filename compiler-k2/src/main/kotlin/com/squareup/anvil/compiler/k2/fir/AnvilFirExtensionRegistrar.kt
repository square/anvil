package com.squareup.anvil.compiler.k2.fir

import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.fir.extensions.FirExtensionSessionComponent
import org.jetbrains.kotlin.fir.extensions.FirSupertypeGenerationExtension
import java.util.ServiceLoader

public class AnvilFirExtensionRegistrar(
  private val messageCollector: MessageCollector,
) : FirExtensionRegistrar() {

  override fun ExtensionRegistrarContext.configurePlugin() {

    val context = AnvilFirContext(messageCollector)

    val factories = ServiceLoader.load(
      AnvilFirExtensionFactory::class.java,
      AnvilFirExtensionFactory::class.java.classLoader,
    )

    for (factory in factories) {

      when (factory) {
        is AnvilFirDeclarationGenerationExtension.Factory ->
          (factory.create(context) as FirDeclarationGenerationExtension.Factory).unaryPlus()
        is AnvilFirSupertypeGenerationExtension.Factory ->
          (factory.create(context) as FirSupertypeGenerationExtension.Factory).unaryPlus()
        is AnvilFirExtensionSessionComponent.Factory ->
          (factory.create(context) as FirExtensionSessionComponent.Factory).unaryPlus()
      }
    }
  }
}
