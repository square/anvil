package com.squareup.anvil.compiler

import org.jetbrains.kotlin.config.CompilerConfiguration
import java.io.File
import java.util.Locale

public class CommandLineOptions private constructor(
  public val projectDir: File,
  public val sourceGenFolder: File,
  public val generateFactories: Boolean,
  public val generateFactoriesOnly: Boolean,
  public val disableComponentMerging: Boolean,
  public val trackSourceFiles: Boolean,
  public val willHaveDaggerFactories: Boolean,
) {
  public companion object {
    public val CompilerConfiguration.commandLineOptions: CommandLineOptions
      get() = CommandLineOptions(
        projectDir = getNotNull(gradleProjectDirKey),
        sourceGenFolder = getNotNull(srcGenDirKey),
        generateFactories = get(generateDaggerFactoriesKey, false),
        generateFactoriesOnly = get(generateDaggerFactoriesOnlyKey, false),
        disableComponentMerging = get(disableComponentMergingKey, false),
        trackSourceFiles = get(trackSourceFilesKey, true),
        willHaveDaggerFactories = get(willHaveDaggerFactoriesKey, false),
      )
  }
}
