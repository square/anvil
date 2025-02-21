package com.squareup.anvil.compiler.k2.fir

import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.messages.toLogger
import org.jetbrains.kotlin.fir.extensions.FirExtension
import org.jetbrains.kotlin.util.Logger

public class AnvilFirContext(
  public val messageCollector: MessageCollector,
) {
  public val logger: Logger by lazy(LazyThreadSafetyMode.NONE) {
    messageCollector.toLogger(false)
  }
}

public sealed interface AnvilFirExtension {
  public val anvilFirContext: AnvilFirContext
}

public sealed interface AnvilFirExtensionFactory<T : FirExtension.Factory<*>> {
  public fun create(anvilFirContext: AnvilFirContext): T
}
