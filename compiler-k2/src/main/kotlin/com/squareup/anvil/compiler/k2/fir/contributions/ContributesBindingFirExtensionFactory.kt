package com.squareup.anvil.compiler.k2.fir.contributions

import com.google.auto.service.AutoService
import com.squareup.anvil.compiler.k2.fir.AnvilFirContext
import com.squareup.anvil.compiler.k2.fir.AnvilFirDeclarationGenerationExtension
import com.squareup.anvil.compiler.k2.fir.AnvilFirExtensionFactory
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension

@AutoService(AnvilFirExtensionFactory::class)
internal class ContributesBindingFirExtensionFactory : AnvilFirDeclarationGenerationExtension.Factory {

  override fun create(anvilFirContext: AnvilFirContext): FirDeclarationGenerationExtension.Factory {
    return FirDeclarationGenerationExtension.Factory {
      ContributesBindingFirExtension(anvilFirContext = anvilFirContext, session = it)
    }
  }
}
