package com.squareup.anvil.compiler.internal.testing.k2

import com.google.auto.service.AutoService
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtension
import kotlin.reflect.KClass

@AutoService(CompilerPluginRegistrar::class)
internal class Compile2CompilerPluginRegistrar : CompilerPluginRegistrar() {
  override val supportsK2: Boolean
    get() = true

  override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
    // FirExtensionRegistrarAdapter.registerExtension(AnvilFirExtensionRegistrar())

    // IrGenerationExtension.registerExtension(CanaryIrMerger())
    //
    // val firExtensions = threadLocalParams.get().firExtensions
    //
    // FirExtensionRegistrarAdapter.registerExtension(
    //   object : FirExtensionRegistrar() {
    //     override fun ExtensionRegistrarContext.configurePlugin() {
    //
    //       firExtensions.forEach {
    //       }
    //     }
    //   },
    // )
    //
    // with(Kapt4CompilerPluginRegistrar()) {
    //   registerExtensions(configuration)
    // }
  }

  companion object {
    internal val threadLocalParams = ThreadLocal<Compile2RegistrarParams>()
  }

  data class Compile2RegistrarParams(
    val firExtensions: List<KClass<FirExtension>>,
  )
}
