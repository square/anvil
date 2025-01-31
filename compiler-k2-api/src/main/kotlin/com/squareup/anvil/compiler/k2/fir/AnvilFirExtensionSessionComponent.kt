package com.squareup.anvil.compiler.k2.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.FirExtensionSessionComponent

public abstract class AnvilFirExtensionSessionComponent(
  override val anvilFirContext: AnvilFirContext,
  session: FirSession,
) : FirExtensionSessionComponent(session),
  AnvilFirExtension {

  public fun interface Factory : AnvilFirExtensionFactory<FirExtensionSessionComponent.Factory>
}
