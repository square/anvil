package com.squareup.anvil.compiler.k2.fir

import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.messages.toLogger
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.FirExtension
import org.jetbrains.kotlin.util.Logger

public class AnvilFirContext(
  public val messageCollector: MessageCollector,
) {
  public val logger: Logger by lazy(LazyThreadSafetyMode.NONE) {
    messageCollector.toLogger(false)
  }
}

public class AnvilFirContext2(
  public val session: FirSession,
  public val messageCollector: MessageCollector,
) {
  public val logger: Logger by lazy(LazyThreadSafetyMode.NONE) {
    messageCollector.toLogger(false)
  }
}

public val AnvilFirContext.session: FirSession
  get() = TODO("Not done yet")

public interface HasAnvilFirContext {
  public val anvilFirContext: AnvilFirContext2
}

public sealed interface AnvilFirExtension

public sealed interface AnvilFirExtensionFactory<T : FirExtension.Factory<*>> {
  public fun create(anvilFirContext: AnvilFirContext): T
}
