package com.squareup.anvil.compiler.k2.fir

import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.anvil.annotations.internal.InternalAnvilApi
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.caches.FirLazyValue
import org.jetbrains.kotlin.fir.caches.firCachesFactory
import org.jetbrains.kotlin.fir.extensions.FirExtensionSessionComponent
import org.jetbrains.kotlin.fir.extensions.predicate.LookupPredicate
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider

public abstract class AnvilFirExtensionSessionComponent(
  session: FirSession,
) : FirExtensionSessionComponent(session) {

  protected inline fun <T, R> FirLazyValue<T>.map(
    crossinline transform: (T) -> R,
  ): FirLazyValue<R> = session.firCachesFactory.createLazyValue { transform(this.getValue()) }

  protected inline fun <T> lazyValue(crossinline initializer: () -> T): FirLazyValue<T> {

    return session.firCachesFactory.createLazyValue { initializer() }
  }

  protected inline fun <reified T> lazySymbols(predicate: LookupPredicate): FirLazyValue<List<T>> {
    return lazyValue {
      session.predicateBasedProvider.getSymbolsByPredicate(predicate)
        .filterIsInstance<T>()
    }
  }
}

@InternalAnvilApi
@ExperimentalAnvilApi
public object AdditionalProcessorsHolder {
  public val additionalProcessors: ThreadLocal<List<AnvilFirProcessor.Factory>> =
    ThreadLocal.withInitial { emptyList() }
}
