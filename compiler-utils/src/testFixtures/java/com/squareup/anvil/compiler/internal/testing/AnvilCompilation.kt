package com.squareup.anvil.compiler.internal.testing

import com.google.auto.value.processor.AutoAnnotationProcessor
import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.anvil.compiler.AnvilCommandLineProcessor
import com.squareup.anvil.compiler.AnvilComponentRegistrar
import com.squareup.anvil.compiler.api.CodeGenerator
import com.squareup.anvil.compiler.ksp.AnvilSymbolProcessor
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.KotlinCompilation.Result
import com.tschuchort.compiletesting.PluginOption
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.addPreviousResultToClasspath
import com.tschuchort.compiletesting.symbolProcessorProviders
import dagger.internal.codegen.ComponentProcessor
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.config.JvmTarget
import java.io.File
import java.io.OutputStream
import java.nio.file.Files

/**
 * A simple API over a [KotlinCompilation] with extra configuration support for Anvil.
 */
@ExperimentalAnvilApi
public class AnvilCompilation internal constructor(
  val kotlinCompilation: KotlinCompilation
) {

  private var isCompiled = false
  private var anvilConfigured = false

  /** Configures this the Anvil behavior of this compilation. */
  @Suppress("SuspiciousCollectionReassignment")
  @ExperimentalAnvilApi
  public fun configureAnvil(
    daggerAnnotationProcessingMode: DaggerAnnotationProcessingMode? = null,
    generateDaggerFactories: Boolean = false,
    generateDaggerFactoriesOnly: Boolean = false,
    disableComponentMerging: Boolean = false,
    enableExperimentalAnvilApis: Boolean = true,
    codeGenerators: List<CodeGenerator> = emptyList(),
    enableAnvil: Boolean = true,
  ) = apply {
    checkNotCompiled()
    check(!anvilConfigured) { "Anvil should not be configured twice." }

    anvilConfigured = true

    if (!enableAnvil) return@apply

    kotlinCompilation.apply {
      // Deprecation tracked in https://github.com/square/anvil/issues/672
      @Suppress("DEPRECATION")
      componentRegistrars = listOf(
        AnvilComponentRegistrar().also { it.addCodeGenerators(codeGenerators) }
      )

      when (daggerAnnotationProcessingMode) {
        DaggerAnnotationProcessingMode.KAPT -> {
          annotationProcessors = listOf(ComponentProcessor(), AutoAnnotationProcessor())
        }
        DaggerAnnotationProcessingMode.KSP -> {
          symbolProcessorProviders = listOf(AnvilSymbolProcessor.Provider())
        }
        null -> {
          // Do nothing
        }
      }

      val anvilCommandLineProcessor = AnvilCommandLineProcessor()
      commandLineProcessors = listOf(anvilCommandLineProcessor)

      pluginOptions = listOf(
        PluginOption(
          pluginId = anvilCommandLineProcessor.pluginId,
          optionName = "src-gen-dir",
          optionValue = File(workingDir, "build/anvil").absolutePath
        ),
        PluginOption(
          pluginId = anvilCommandLineProcessor.pluginId,
          optionName = "generate-dagger-factories",
          optionValue = generateDaggerFactories.toString()
        ),
        PluginOption(
          pluginId = anvilCommandLineProcessor.pluginId,
          optionName = "generate-dagger-factories-only",
          optionValue = generateDaggerFactoriesOnly.toString()
        ),
        PluginOption(
          pluginId = anvilCommandLineProcessor.pluginId,
          optionName = "disable-component-merging",
          optionValue = disableComponentMerging.toString()
        )
      )

      if (enableExperimentalAnvilApis) {
        kotlincArguments += listOf(
          "-opt-in=kotlin.RequiresOptIn",
          "-opt-in=com.squareup.anvil.annotations.ExperimentalAnvilApi"
        )
      }
    }
  }

  /** Adds the given sources to this compilation with their packages and names inferred. */
  public fun addSources(@Language("kotlin") vararg sources: String) = apply {
    checkNotCompiled()
    kotlinCompilation.sources += sources.mapIndexed { index, content ->
      val packageDir = content.lines()
        .firstOrNull { it.trim().startsWith("package ") }
        ?.substringAfter("package ")
        ?.replace('.', '/')
        ?.let { "$it/" }
        ?: ""

      val name = "${kotlinCompilation.workingDir.absolutePath}/sources/src/main/java/" +
        "$packageDir/Source$index.kt"

      Files.createDirectories(File(name).parentFile.toPath())

      SourceFile.kotlin(name, contents = content, trimIndent = true)
    }
  }

  public fun addPreviousCompilationResult(result: Result) = apply {
    checkNotCompiled()
    kotlinCompilation.addPreviousResultToClasspath(result)
  }

  public fun jvmTarget(jvmTarget: JvmTarget) {
    checkNotCompiled()
    kotlinCompilation.jvmTarget = jvmTarget.description
  }

  /**
   * Returns an Anvil-generated file with the given [packageName] and [fileName] from its expected
   * path.
   */
  public fun generatedAnvilFile(
    packageName: String,
    fileName: String
  ): File {
    check(isCompiled) {
      "No compilation run yet! Call compile() first."
    }
    return File(
      kotlinCompilation.workingDir,
      "build/anvil/${packageName.replace('.', File.separatorChar)}/$fileName.kt"
    )
      .apply {
        check(exists()) {
          "Generated file not found!"
        }
      }
  }

  private fun checkNotCompiled() {
    check(!isCompiled) {
      "Already compiled! Create a new compilation if you want to compile again."
    }
  }

  /**
   * Compiles the underlying [KotlinCompilation]. Note that if [configureAnvil] has not been called
   * prior to this, it will be configured with default behavior.
   */
  public fun compile(
    @Language("kotlin") vararg sources: String,
    block: Result.() -> Unit = {}
  ): Result {
    checkNotCompiled()
    if (!anvilConfigured) {
      // Configure with default behaviors
      configureAnvil()
    }
    addSources(*sources)
    isCompiled = true

    return kotlinCompilation.compile().apply(block)
  }

  companion object {
    public operator fun invoke(): AnvilCompilation {
      return AnvilCompilation(
        KotlinCompilation().apply {
          // Sensible default behaviors
          inheritClassPath = true
          jvmTarget = JvmTarget.JVM_1_8.description
          verbose = false
        }
      )
    }
  }
}

/** Available Dagger annotation processing modes. */
enum class DaggerAnnotationProcessingMode {
  KAPT,
  KSP
}

/**
 * Helpful for testing code generators in unit tests end to end.
 *
 * This covers common cases, but is built upon reusable logic in [AnvilCompilation] and
 * [AnvilCompilation.configureAnvil]. Consider using those APIs if more advanced configuration
 * is needed.
 */
@ExperimentalAnvilApi
public fun compileAnvil(
  @Language("kotlin") vararg sources: String,
  daggerAnnotationProcessingMode: DaggerAnnotationProcessingMode? = null,
  generateDaggerFactories: Boolean = false,
  generateDaggerFactoriesOnly: Boolean = false,
  disableComponentMerging: Boolean = false,
  allWarningsAsErrors: Boolean = true,
  messageOutputStream: OutputStream = System.out,
  workingDir: File? = null,
  enableExperimentalAnvilApis: Boolean = true,
  previousCompilationResult: Result? = null,
  codeGenerators: List<CodeGenerator> = emptyList(),
  moduleName: String? = null,
  jvmTarget: JvmTarget? = null,
  block: Result.() -> Unit = { },
): Result {
  return AnvilCompilation()
    .apply {
      kotlinCompilation.apply {
        this.allWarningsAsErrors = allWarningsAsErrors
        this.messageOutputStream = messageOutputStream
        if (workingDir != null) {
          this.workingDir = workingDir
        }
        if (moduleName != null) {
          this.moduleName = moduleName
        }
      }

      if (jvmTarget != null) {
        jvmTarget(jvmTarget)
      }

      if (previousCompilationResult != null) {
        addPreviousCompilationResult(previousCompilationResult)
      }
    }
    .configureAnvil(
      daggerAnnotationProcessingMode = daggerAnnotationProcessingMode,
      generateDaggerFactories = generateDaggerFactories,
      generateDaggerFactoriesOnly = generateDaggerFactoriesOnly,
      disableComponentMerging = disableComponentMerging,
      enableExperimentalAnvilApis = enableExperimentalAnvilApis,
      codeGenerators = codeGenerators,
    )
    .compile(*sources)
    .also(block)
}
