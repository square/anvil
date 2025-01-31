package com.squareup.anvil.compiler.k2.fir

import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.fir.extensions.FirExtension

public class AnvilFirContext(
  public val messageCollector: MessageCollector,
)

public sealed interface AnvilFirExtension {
  public val anvilFirContext: AnvilFirContext
}

public sealed interface AnvilFirExtensionFactory<T : FirExtension.Factory<*>> {
  public fun create(anvilFirContext: AnvilFirContext): T
}
