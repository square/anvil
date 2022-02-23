package com.squareup.anvil.compiler

import com.google.auto.service.AutoService
import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.anvil.compiler.CommandLineOptions.Companion.commandLineOptions
import com.squareup.anvil.compiler.api.CodeGenerator
import com.squareup.anvil.compiler.codegen.BindingModuleGenerator
import com.squareup.anvil.compiler.codegen.CodeGenerationExtension
import com.squareup.anvil.compiler.codegen.ContributesSubcomponentHandlerGenerator
import com.squareup.anvil.compiler.codegen.reference.RealAnvilModuleDescriptor
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.codegen.extensions.ExpressionCodegenExtension
import org.jetbrains.kotlin.com.intellij.mock.MockProject
import org.jetbrains.kotlin.com.intellij.openapi.extensions.LoadingOrder
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.resolve.extensions.SyntheticResolveExtension
import org.jetbrains.kotlin.resolve.jvm.extensions.AnalysisHandlerExtension
import java.io.File
import java.util.ServiceLoader

/**
 * Entry point for the Anvil Kotlin compiler plugin. It registers several callbacks for the
 * various compilation phases.
 */
@AutoService(ComponentRegistrar::class)
class AnvilComponentRegistrar : ComponentRegistrar {

  private val manuallyAddedCodeGenerators = mutableListOf<CodeGenerator>()

  override fun registerProjectComponents(
    project: MockProject,
    configuration: CompilerConfiguration
  ) {
    val sourceGenFolder = File(configuration.getNotNull(srcGenDirKey))
    val scanner = ClassScanner()
    val commandLineOptions = configuration.commandLineOptions

    val codeGenerators = loadCodeGenerators() +
      manuallyAddedCodeGenerators +
      // We special case these due to the ClassScanner requirement.
      BindingModuleGenerator(scanner) +
      ContributesSubcomponentHandlerGenerator(scanner)

    val moduleDescriptorFactory = RealAnvilModuleDescriptor.Factory()

    // It's important to register our extension at the first position. The compiler calls each
    // extension one by one. If an extension returns a result, then the compiler won't call any
    // other extension. That usually happens with Kapt in the stub generating task.
    //
    // It's not dangerous for our extension to run first, because we generate code, restart the
    // analysis phase and then don't return a result anymore. That means the next extension can
    // take over. If we wouldn't do this and any other extension won't let ours run, then we
    // couldn't generate any code.
    AnalysisHandlerExtension.registerExtensionFirst(
      project,
      CodeGenerationExtension(
        codeGenDir = sourceGenFolder,
        codeGenerators = codeGenerators,
        commandLineOptions = commandLineOptions,
        moduleDescriptorFactory = moduleDescriptorFactory
      )
    )

    if (!commandLineOptions.generateFactoriesOnly && !commandLineOptions.disableComponentMerging) {
      SyntheticResolveExtension.registerExtension(
        project,
        InterfaceMerger(scanner, moduleDescriptorFactory)
      )
      ExpressionCodegenExtension.registerExtension(
        project,
        ModuleMerger(scanner, moduleDescriptorFactory)
      )

      IrGenerationExtension.registerExtension(
        project,
        ModuleMergerIr(scanner, moduleDescriptorFactory)
      )
    }
  }

  private fun AnalysisHandlerExtension.Companion.registerExtensionFirst(
    project: MockProject,
    extension: AnalysisHandlerExtension
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
  fun addCodeGenerators(codeGenerators: List<CodeGenerator>) {
    manuallyAddedCodeGenerators += codeGenerators
  }
}
