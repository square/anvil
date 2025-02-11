package com.squareup.anvil.compiler.k2.fir.contributions

import com.google.auto.service.AutoService
import com.squareup.anvil.compiler.k2.fir.AnvilFirContext
import com.squareup.anvil.compiler.k2.fir.AnvilFirExtensionFactory
import com.squareup.anvil.compiler.k2.fir.AnvilFirExtensionSessionComponent
import org.jetbrains.kotlin.fir.extensions.FirExtensionSessionComponent

@AutoService(AnvilFirExtensionFactory::class)
internal class ContributesBindingSessionComponentFactory : AnvilFirExtensionSessionComponent.Factory {

  override fun create(anvilFirContext: AnvilFirContext): FirExtensionSessionComponent.Factory {
    return FirExtensionSessionComponent.Factory {
      ContributesBindingSessionComponent(anvilFirContext = anvilFirContext, session = it)
    }
  }
}
