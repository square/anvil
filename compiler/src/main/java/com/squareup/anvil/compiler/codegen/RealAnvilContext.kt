package com.squareup.anvil.compiler.codegen

import com.squareup.anvil.compiler.api.AnvilContext

internal data class RealAnvilContext(
  override val generateFactories: Boolean,
  override val generateFactoriesOnly: Boolean,
  override val disableComponentMerging: Boolean
) : AnvilContext
