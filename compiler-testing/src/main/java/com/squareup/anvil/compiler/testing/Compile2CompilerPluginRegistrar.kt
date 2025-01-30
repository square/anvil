package com.squareup.anvil.compiler.testing

import com.google.auto.service.AutoService
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.FirSession
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
import kotlin.reflect.KFunction1

@AutoService(CompilerPluginRegistrar::class)
internal class Compile2CompilerPluginRegistrar : CompilerPluginRegistrar() {
  override val supportsK2: Boolean
    get() = true

  override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
    val firExtensions = threadLocalParams.get().firExtensions

    FirExtensionRegistrarAdapter.registerExtension(
      object : FirExtensionRegistrar() {
        override fun ExtensionRegistrarContext.configurePlugin() {

          for (factory in firExtensions) {
            @Suppress("UNCHECKED_CAST")
            when (factory.returnType.classifier) {
              FirStatusTransformerExtension::class ->
                (factory as KFunction1<FirSession, FirStatusTransformerExtension>).unaryPlus()
              FirDeclarationGenerationExtension::class ->
                (factory as KFunction1<FirSession, FirDeclarationGenerationExtension>).unaryPlus()
              FirAdditionalCheckersExtension::class ->
                (factory as KFunction1<FirSession, FirAdditionalCheckersExtension>).unaryPlus()
              FirSupertypeGenerationExtension::class ->
                (factory as KFunction1<FirSession, FirSupertypeGenerationExtension>).unaryPlus()
              FirTypeAttributeExtension::class ->
                (factory as KFunction1<FirSession, FirTypeAttributeExtension>).unaryPlus()
              FirExpressionResolutionExtension::class ->
                (factory as KFunction1<FirSession, FirExpressionResolutionExtension>).unaryPlus()
              FirExtensionSessionComponent::class ->
                (factory as KFunction1<FirSession, FirExtensionSessionComponent>).unaryPlus()
              FirSamConversionTransformerExtension::class ->
                (factory as KFunction1<FirSession, FirSamConversionTransformerExtension>).unaryPlus()
              FirAssignExpressionAltererExtension::class ->
                (factory as KFunction1<FirSession, FirAssignExpressionAltererExtension>).unaryPlus()
              FirScriptConfiguratorExtension::class ->
                (factory as KFunction1<FirSession, FirScriptConfiguratorExtension>).unaryPlus()
              FirScriptResolutionConfigurationExtension::class ->
                (factory as KFunction1<FirSession, FirScriptResolutionConfigurationExtension>).unaryPlus()
              Fir2IrScriptConfiguratorExtension::class ->
                (factory as KFunction1<FirSession, Fir2IrScriptConfiguratorExtension>).unaryPlus()
              FirFunctionTypeKindExtension::class ->
                (factory as KFunction1<FirSession, FirFunctionTypeKindExtension>).unaryPlus()
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
    val firExtensions: List<KFunction1<FirSession, FirExtension>>,
  )
}
