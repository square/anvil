package com.squareup.anvil.compiler

import com.google.auto.service.AutoService
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.squareup.anvil.compiler.ClassScanningKspProcessor.Provider
import com.squareup.anvil.compiler.api.AnvilContext
import com.squareup.anvil.compiler.api.AnvilKspExtension
import com.squareup.anvil.compiler.api.ComponentMergingBackend
import com.squareup.anvil.compiler.codegen.KspContributesSubcomponentHandler
import com.squareup.anvil.compiler.codegen.KspContributesSubcomponentHandlerSymbolProcessor
import com.squareup.anvil.compiler.codegen.ksp.AnvilSymbolProcessor
import com.squareup.anvil.compiler.codegen.ksp.AnvilSymbolProcessorProvider
import com.squareup.anvil.compiler.codegen.ksp.KspTracerImpl
import com.squareup.anvil.compiler.codegen.toAnvilContext
import java.util.ServiceLoader

/**
 * An abstraction layer between [KspContributesSubcomponentHandlerSymbolProcessor] and [KspContributionMerger]
 * to handle running them contextually and with a shared caching [ClassScannerKsp].
 *
 * @see Provider for more details on the logic of when each are used.
 */
internal class ClassScanningKspProcessor(
  override val env: SymbolProcessorEnvironment,
  private val delegates: List<SymbolProcessor>,
  private val classScanner: ClassScannerKsp,
) : AnvilSymbolProcessor() {

  @AutoService(SymbolProcessorProvider::class)
  class Provider : AnvilSymbolProcessorProvider(
    { context ->
      // Both qualify to run if we're not only generating factories
      !context.generateFactoriesOnly
    },
    { env ->
      val context = env.toAnvilContext()

      // Shared caching class scanner for both processors
      val classScanner = ClassScannerKsp(KspTracerImpl(env, "ClassScannerKsp"))

      // Extensions to run
      val extensions = extensions(env, context)

      val enableContributesSubcomponentHandling =
        env.options[OPTION_ENABLE_CONTRIBUTES_SUBCOMPONENT_MERGING]
          ?.toBoolean() ?: true

      // ContributesSubcomponent handler, which will always be run but needs to conditionally run
      // within KspContributionMerger if it's going to run.
      val contributesSubcomponentHandler = if (enableContributesSubcomponentHandling) {
        KspContributesSubcomponentHandlerSymbolProcessor(env, classScanner)
      } else {
        KspContributesSubcomponentHandler.NoOp
      }

      val componentMergingEnabled =
        !context.disableComponentMerging &&
          context.componentMergingBackend == ComponentMergingBackend.KSP

      val delegates = if (componentMergingEnabled) {
        // We're running component merging, so we need to run both and let KspContributionMerger
        // handle running the contributesSubcomponentHandler when needed.
        listOf(KspContributionMerger(env, classScanner, contributesSubcomponentHandler, extensions))
      } else if (enableContributesSubcomponentHandling) {
        // We're only generating factories/contributessubcomponents, so only run it + extensions
        listOf(
          contributesSubcomponentHandler as SymbolProcessor,
          AnvilKspExtensionsRunner(extensions),
        )
      } else {
        listOf(AnvilKspExtensionsRunner(extensions))
      }
      ClassScanningKspProcessor(env, delegates, classScanner)
    },
  )

  override fun processChecked(resolver: Resolver): List<KSAnnotated> {
    classScanner.startRound(resolver)
    return delegates.flatMap { it.process(resolver) }
      .also {
        if (it.isNotEmpty()) {
          classScanner.ensureInRoundHintsCaptured()
        }
        classScanner.endRound()
      }
  }

  override fun finish() {
    delegates.forEach { it.finish() }
  }

  override fun onError() {
    delegates.forEach { it.onError() }
  }

  companion object {
    private fun extensions(
      env: SymbolProcessorEnvironment,
      context: AnvilContext,
    ): Set<AnvilKspExtension> {
      return try {
        ServiceLoader.load(
          AnvilKspExtension.Provider::class.java,
          AnvilKspExtension.Provider::class.java.classLoader,
        ).mapNotNullTo(mutableSetOf()) { provider ->
          if (provider.isApplicable(context)) {
            provider.create(env)
          } else {
            null
          }
        }
      } catch (e: Exception) {
        env.logger.exception(e)
        emptySet()
      }
    }
  }

  /**
   * A simple [SymbolProcessor] that can run a set of [AnvilKspExtension]s.
   */
  private class AnvilKspExtensionsRunner(
    private val extensions: Set<AnvilKspExtension>,
  ) : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
      return extensions.flatMap { it.process(resolver) }
    }
  }
}
