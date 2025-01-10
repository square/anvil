package com.squareup.anvil.compiler.testing

import com.google.auto.service.AutoService
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.backend.Fir2IrScriptConfiguratorExtension
import org.jetbrains.kotlin.fir.builder.FirScriptConfiguratorExtension
import org.jetbrains.kotlin.fir.extensions.FirAssignExpressionAltererExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirExpressionResolutionExtension
import org.jetbrains.kotlin.fir.extensions.FirExtension
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter
import org.jetbrains.kotlin.fir.extensions.FirExtensionSessionComponent
import org.jetbrains.kotlin.fir.extensions.FirFunctionTypeKindExtension
import org.jetbrains.kotlin.fir.extensions.FirScriptResolutionConfigurationExtension
import org.jetbrains.kotlin.fir.extensions.FirStatusTransformerExtension
import org.jetbrains.kotlin.fir.extensions.FirSupertypeGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirTypeAttributeExtension
import org.jetbrains.kotlin.fir.resolve.FirSamConversionTransformerExtension
import org.jetbrains.kotlin.kapt4.Kapt4CompilerPluginRegistrar
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

@AutoService(CompilerPluginRegistrar::class)
internal class Compile2CompilerPluginRegistrar : CompilerPluginRegistrar() {
  override val supportsK2: Boolean
    get() = true

  override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
    // FirExtensionRegistrarAdapter.registerExtension(AnvilFirExtensionRegistrar())

    // IrGenerationExtension.registerExtension(CanaryIrMerger())

    val firExtensions = threadLocalParams.get().firExtensions

    FirExtensionRegistrarAdapter.registerExtension(
      object : FirExtensionRegistrar() {
        override fun ExtensionRegistrarContext.configurePlugin() {

          firExtensions.forEach { clazz ->

            when {
              clazz.isSubclassOf(FirStatusTransformerExtension::class) -> {}
              clazz.isSubclassOf(FirDeclarationGenerationExtension::class) -> {}
              clazz.isSubclassOf(FirAdditionalCheckersExtension::class) -> {}
              clazz.isSubclassOf(FirSupertypeGenerationExtension::class) -> {}
              clazz.isSubclassOf(FirTypeAttributeExtension::class) -> {}
              clazz.isSubclassOf(FirExpressionResolutionExtension::class) -> {}
              clazz.isSubclassOf(FirExtensionSessionComponent::class) -> {}
              clazz.isSubclassOf(FirSamConversionTransformerExtension::class) -> {}
              clazz.isSubclassOf(FirAssignExpressionAltererExtension::class) -> {}
              clazz.isSubclassOf(FirScriptConfiguratorExtension::class) -> {}
              clazz.isSubclassOf(FirScriptResolutionConfigurationExtension::class) -> {}
              clazz.isSubclassOf(Fir2IrScriptConfiguratorExtension::class) -> {}
              clazz.isSubclassOf(FirFunctionTypeKindExtension::class) -> {}
              else -> error("unsupported fir extension: ${clazz.qualifiedName}")
            }
          }
        }
      },
    )

    with(Kapt4CompilerPluginRegistrar()) {
      registerExtensions(configuration)
    }
  }

  companion object {
    internal val threadLocalParams = ThreadLocal<Compile2RegistrarParams>()
  }

  data class Compile2RegistrarParams(
    val firExtensions: List<KClass<FirExtension>>,
  )
}
