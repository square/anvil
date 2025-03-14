package com.squareup.anvil.compiler.k2.fir.abstraction.providers

import com.squareup.anvil.compiler.k2.fir.AnvilFirContext2
import com.squareup.anvil.compiler.k2.fir.AnvilFirProcessor
import com.squareup.anvil.compiler.k2.fir.FlushingSupertypeProcessor
import com.squareup.anvil.compiler.k2.fir.SupertypeProcessor
import com.squareup.anvil.compiler.k2.fir.TopLevelClassProcessor
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.caches.FirLazyValue
import org.jetbrains.kotlin.fir.caches.firCachesFactory
import org.jetbrains.kotlin.fir.caches.getValue
import org.jetbrains.kotlin.fir.extensions.FirExtensionSessionComponent
import java.util.ServiceLoader

public val FirSession.anvilFirProcessorProvider: AnvilFirProcessorProvider by FirSession.sessionComponentAccessor()

public class AnvilFirProcessorProvider(
  anvilFirContext: AnvilFirContext2,
) : FirExtensionSessionComponent(anvilFirContext.session) {

  private val cachesFactory = session.firCachesFactory

  private val processors by createLazyValue {
    ServiceLoader.load(AnvilFirProcessor.Factory::class.java)
      .map { it.create(anvilFirContext) }
      .sortedBy { it::class.qualifiedName }
      .groupBy {
        when (it) {
          is FlushingSupertypeProcessor -> FlushingSupertypeProcessor::class
          is SupertypeProcessor -> SupertypeProcessor::class
          is TopLevelClassProcessor -> TopLevelClassProcessor::class
        }
      }
  }

  public val topLevelClassProcessors: List<TopLevelClassProcessor> by createLazyValue {
    @Suppress("UNCHECKED_CAST")
    processors[TopLevelClassProcessor::class].orEmpty() as List<TopLevelClassProcessor>
  }

  public val supertypeProcessors: List<SupertypeProcessor> by createLazyValue {
    @Suppress("UNCHECKED_CAST")
    processors[SupertypeProcessor::class].orEmpty() as List<SupertypeProcessor>
  }
  public val flushingSupertypeProcessors: List<FlushingSupertypeProcessor> by createLazyValue {
    @Suppress("UNCHECKED_CAST")
    processors[FlushingSupertypeProcessor::class].orEmpty() as List<FlushingSupertypeProcessor>
  }

  private fun <V> createLazyValue(createValue: () -> V): FirLazyValue<V> =
    cachesFactory.createLazyValue(createValue)
}
