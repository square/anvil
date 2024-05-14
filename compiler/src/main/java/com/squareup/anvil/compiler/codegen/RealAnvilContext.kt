package com.squareup.anvil.compiler.codegen

import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.squareup.anvil.compiler.CommandLineOptions
import com.squareup.anvil.compiler.api.AnvilContext
import com.squareup.anvil.compiler.api.ComponentMergingBackend
import com.squareup.anvil.compiler.disableComponentMergingName
import com.squareup.anvil.compiler.generateDaggerFactoriesName
import com.squareup.anvil.compiler.generateDaggerFactoriesOnlyName
import com.squareup.anvil.compiler.mergingBackendName
import com.squareup.anvil.compiler.willHaveDaggerFactoriesName
import org.jetbrains.kotlin.descriptors.ModuleDescriptor

internal data class RealAnvilContext(
  override val generateFactories: Boolean,
  override val generateFactoriesOnly: Boolean,
  override val disableComponentMerging: Boolean,
  override val trackSourceFiles: Boolean,
  override val willHaveDaggerFactories: Boolean,
  override val componentMergingBackend: ComponentMergingBackend,
  val nullableModule: ModuleDescriptor?,
) : AnvilContext {
  override val module: ModuleDescriptor
    get() = nullableModule ?: error("Module is not available in KSP.")
}

internal fun CommandLineOptions.toAnvilContext(
  module: ModuleDescriptor,
): RealAnvilContext = RealAnvilContext(
  generateFactories = generateFactories,
  generateFactoriesOnly = generateFactoriesOnly,
  disableComponentMerging = disableComponentMerging,
  trackSourceFiles = trackSourceFiles,
  willHaveDaggerFactories = willHaveDaggerFactories,
  componentMergingBackend = componentMergingBackend,
  nullableModule = module,
)

internal fun SymbolProcessorEnvironment.toAnvilContext(): AnvilContext = RealAnvilContext(
  generateFactories = options.booleanOption(generateDaggerFactoriesName),
  generateFactoriesOnly = options.booleanOption(generateDaggerFactoriesOnlyName),
  disableComponentMerging = options.booleanOption(disableComponentMergingName),
  trackSourceFiles = false,
  willHaveDaggerFactories = options.booleanOption(willHaveDaggerFactoriesName),
  componentMergingBackend = options[mergingBackendName]?.let(ComponentMergingBackend::fromString) ?: ComponentMergingBackend.IR,
  nullableModule = null,
)

private fun Map<String, String>.booleanOption(key: String, default: Boolean = false): Boolean {
  return get(key)?.toBoolean() ?: default
}
