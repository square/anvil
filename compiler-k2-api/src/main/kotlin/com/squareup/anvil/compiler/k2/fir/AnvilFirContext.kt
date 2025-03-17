package com.squareup.anvil.compiler.k2.fir

import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.messages.toLogger
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.FirExtensionSessionComponent
import org.jetbrains.kotlin.util.Logger

public val FirSession.anvilContext: AnvilFirContext by FirSession.sessionComponentAccessor()

public class AnvilFirContext(
  public val messageCollector: MessageCollector,
  session: FirSession,
) : FirExtensionSessionComponent(session) {
  public val logger: Logger by lazy(LazyThreadSafetyMode.NONE) {
    messageCollector.toLogger(treatWarningsAsErrors = false)
  }
}

public interface HasAnvilFirContext {
  public val anvilContext: AnvilFirContext
}
