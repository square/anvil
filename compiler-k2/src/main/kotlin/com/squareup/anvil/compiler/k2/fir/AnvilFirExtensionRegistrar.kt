package com.squareup.anvil.compiler.k2.fir

import com.squareup.anvil.compiler.k2.constructor.inject.FirInjectConstructorFactoryGenerationExtension
import com.squareup.anvil.compiler.k2.fir.abstraction.extensions.SupertypeProcessorExtension
import com.squareup.anvil.compiler.k2.fir.abstraction.extensions.TopLevelClassProcessorExtension
import com.squareup.anvil.compiler.k2.fir.abstraction.providers.AnvilFirDependencyHintProvider
import com.squareup.anvil.compiler.k2.fir.abstraction.providers.AnvilFirProcessorProvider
import com.squareup.anvil.compiler.k2.fir.abstraction.providers.AnvilFirSymbolProvider
import com.squareup.anvil.compiler.k2.fir.abstraction.providers.ScopedContributionProvider
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.fir.extensions.FirExtensionSessionComponent
import java.util.ServiceLoader

public class AnvilFirExtensionRegistrar(
  private val messageCollector: MessageCollector,
) : FirExtensionRegistrar() {

  override fun ExtensionRegistrarContext.configurePlugin() {

    +FirExtensionSessionComponent.Factory { AnvilFirContext(messageCollector, it) }

    +::SupertypeProcessorExtension
    +::TopLevelClassProcessorExtension

    +::AnvilFirDependencyHintProvider
    +::AnvilFirProcessorProvider
    +::AnvilFirSymbolProvider
    +::ScopedContributionProvider

    +::FirInjectConstructorFactoryGenerationExtension

    val factories = ServiceLoader.load(FirExtensionSessionComponent.Factory::class.java)

    for (factory in factories) {
      +factory
    }
  }
}
