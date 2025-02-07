package com.squareup.anvil.compiler.k2.constructor.inject

import com.google.auto.service.AutoService
import com.squareup.anvil.compiler.k2.fir.AnvilFirContext
import com.squareup.anvil.compiler.k2.fir.AnvilFirDeclarationGenerationExtension
import com.squareup.anvil.compiler.k2.fir.AnvilFirExtensionFactory
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension

@AutoService(AnvilFirExtensionFactory::class)
internal class FirInjectConstructorFactoryGenerationExtensionFactory :
  AnvilFirDeclarationGenerationExtension.Factory {
  override fun create(
    anvilFirContext: AnvilFirContext,
  ): FirDeclarationGenerationExtension.Factory = FirDeclarationGenerationExtension.Factory {
    FirInjectConstructorFactoryGenerationExtension(
      anvilFirContext = anvilFirContext,
      session = it,
    )
  }
}
