// Deprecation tracked in https://github.com/square/anvil/issues/672
@file:Suppress("DEPRECATION")

package com.squareup.anvil.compiler

import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.anvil.compiler.CommandLineOptions.Companion.commandLineOptions
import com.squareup.anvil.compiler.api.CodeGenerator
import org.jetbrains.kotlin.com.intellij.mock.MockProject
import org.jetbrains.kotlin.com.intellij.openapi.extensions.LoadingOrder
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.config.languageVersionSettings
import org.jetbrains.kotlin.resolve.jvm.extensions.AnalysisHandlerExtension
import java.util.ServiceLoader
import kotlin.LazyThreadSafetyMode.NONE

/**
 * Entry point for the Anvil Kotlin compiler plugin. It registers several callbacks for the
 * various compilation phases.
 */
// @AutoService(ComponentRegistrar::class)
public class AnvilComponentRegistrar : ComponentRegistrar {

  // We don't do anything for K2, but we need to return true here or the compilation fails.
  override val supportsK2: Boolean = true

  private val manuallyAddedCodeGenerators = mutableListOf<CodeGenerator>()

  override fun registerProjectComponents(
    project: MockProject,
    configuration: CompilerConfiguration,
  ) {
    if (configuration.languageVersionSettings.languageVersion >= LanguageVersion.KOTLIN_2_0) {
      return
    }

    val commandLineOptions = configuration.commandLineOptions

    val irMergesFile by lazy(NONE) { configuration.getNotNull(irMergesFileKey) }
    val trackSourceFiles = configuration.getNotNull(trackSourceFilesKey)

    val mergingEnabled =
      !commandLineOptions.generateFactoriesOnly && !commandLineOptions.disableComponentMerging
    if (mergingEnabled) {

    }

    // project.extensionArea
    //   .getExtensionPoint(FirSupertypeGenerationExtension.NAME)
    //   .registerExtensionPoint(FirContributionMerger(TODO()))

    val sourceGenFolder = configuration.getNotNull(srcGenDirKey)
    val cacheDir = configuration.getNotNull(anvilCacheDirKey)

    val codeGenerators = loadCodeGenerators() +
      manuallyAddedCodeGenerators
  }

  private fun AnalysisHandlerExtension.Companion.registerExtensionFirst(
    project: MockProject,
    extension: AnalysisHandlerExtension,
  ) {
    project.extensionArea
      .getExtensionPoint(AnalysisHandlerExtension.extensionPointName)
      .registerExtension(extension, LoadingOrder.FIRST, project)
  }

  private fun loadCodeGenerators(): List<CodeGenerator> {
    return ServiceLoader.load(CodeGenerator::class.java, CodeGenerator::class.java.classLoader)
      .toList()
  }

  // This function is used in tests. It must be called before the test code is being compiled.
  @ExperimentalAnvilApi
  public fun addCodeGenerators(codeGenerators: List<CodeGenerator>) {
    manuallyAddedCodeGenerators += codeGenerators
  }
}
