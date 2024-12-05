package com.squareup.anvil.compiler.codegen

import com.squareup.anvil.compiler.CommandLineOptions
import com.squareup.anvil.compiler.api.AnvilContext
import org.jetbrains.kotlin.descriptors.ModuleDescriptor

internal data class RealAnvilContext(
  override val generateFactories: Boolean,
  override val generateFactoriesOnly: Boolean,
  override val disableComponentMerging: Boolean,
  override val trackSourceFiles: Boolean,
  override val willHaveDaggerFactories: Boolean,
  override val module: ModuleDescriptor,
) : AnvilContext

internal fun CommandLineOptions.toAnvilContext(
  module: ModuleDescriptor,
): RealAnvilContext = RealAnvilContext(
  generateFactories = generateFactories,
  generateFactoriesOnly = generateFactoriesOnly,
  disableComponentMerging = disableComponentMerging,
  trackSourceFiles = trackSourceFiles,
  willHaveDaggerFactories = willHaveDaggerFactories,
  module = module,
)
