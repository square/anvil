package com.squareup.anvil.compiler.k2.fir

import com.squareup.anvil.compiler.k2.fir.abstraction.extensions.SupertypeProcessorExtension
import com.squareup.anvil.compiler.k2.fir.abstraction.extensions.TopLevelClassProcessorExtension
import com.squareup.anvil.compiler.k2.fir.abstraction.providers.AnvilFirProcessorProvider
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.fir.extensions.FirExtensionSessionComponent
import java.util.ServiceLoader

public class AnvilFirExtensionRegistrar(
  private val messageCollector: MessageCollector,
) : FirExtensionRegistrar() {

  override fun ExtensionRegistrarContext.configurePlugin() {

    val context = AnvilFirContext(messageCollector)

    FirExtensionSessionComponent.Factory { session ->
      val ctx = AnvilFirContext2(session, messageCollector)
      AnvilFirProcessorProvider(ctx)
    }
      .unaryPlus()

    +::SupertypeProcessorExtension
    +::TopLevelClassProcessorExtension

    val factories = ServiceLoader.load(AnvilFirExtensionFactory::class.java)

    for (factory in factories) {

      when (factory) {
        is AnvilFirDeclarationGenerationExtension.Factory -> factory.create(context).unaryPlus()
        is AnvilFirSupertypeGenerationExtension.Factory -> factory.create(context).unaryPlus()
        is AnvilFirExtensionSessionComponent.Factory -> factory.create(context).unaryPlus()
      }
    }
  }
}
