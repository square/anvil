package com.squareup.anvil.compiler.k2.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.FirSupertypeGenerationExtension

public abstract class AnvilFirSupertypeGenerationExtension(
  public val anvilFirContext: AnvilFirContext,
  session: FirSession,
) : FirSupertypeGenerationExtension(session),
  AnvilFirExtension {
  public fun interface Factory : AnvilFirExtensionFactory<FirSupertypeGenerationExtension.Factory>
}
