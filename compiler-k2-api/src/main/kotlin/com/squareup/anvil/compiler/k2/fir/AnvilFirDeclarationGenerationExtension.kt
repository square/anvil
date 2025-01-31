package com.squareup.anvil.compiler.k2.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension

public abstract class AnvilFirDeclarationGenerationExtension(
  override val anvilFirContext: AnvilFirContext,
  session: FirSession,
) : FirDeclarationGenerationExtension(session),
  AnvilFirExtension {
  public fun interface Factory : AnvilFirExtensionFactory<FirDeclarationGenerationExtension.Factory>
}
