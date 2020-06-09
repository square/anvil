package com.squareup.hephaestus.compiler

import com.google.auto.service.AutoService
import org.jetbrains.kotlin.cli.common.config.KotlinSourceRoot
import org.jetbrains.kotlin.cli.common.config.kotlinSourceRoots
import org.jetbrains.kotlin.codegen.extensions.ExpressionCodegenExtension
import org.jetbrains.kotlin.com.intellij.mock.MockProject
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.resolve.extensions.SyntheticResolveExtension
import org.jetbrains.kotlin.resolve.jvm.extensions.AnalysisHandlerExtension
import java.io.File

/**
 * Entry point for the Hephaestus Kotlin compiler plugin. It registers several callbacks for the
 * various compilation phases.
 */
@AutoService(ComponentRegistrar::class)
class HephaestusComponentRegistrar : ComponentRegistrar {
  override fun registerProjectComponents(
    project: MockProject,
    configuration: CompilerConfiguration
  ) {
    val kotlinSourceRoots = configuration.kotlinSourceRoots.ifEmpty { return }
    val scanner = ClassScanner(createPackageList(kotlinSourceRoots))
    val sourceGenFolder = File(configuration.getNotNull(srcGenDirKey))

    // The analysis phase is skipped when generating stubs for annotation processors. In unit tests
    // we want to test this path, too.
    if (!configuration.get(skipAnalysisKey, false)) {
      AnalysisHandlerExtension.registerExtension(
          project, ContributeToGenerator(sourceGenFolder)
      )
    }

    SyntheticResolveExtension.registerExtension(
        project, InterfaceMerger(scanner)
    )
    ExpressionCodegenExtension.registerExtension(
        project, ModuleMerger(scanner)
    )
  }

  private fun createPackageList(sourceRoots: List<KotlinSourceRoot>): List<String> {
    return sourceRoots
        .map { File(it.path) }
        .mapNotNull { srcFile ->
          val srcDir = generateSequence(srcFile) { it.parentFile }
              .firstOrNull { it.name == "java" || it.name == "kotlin" } ?: return@mapNotNull null

          srcFile.parentFile.relativeTo(srcDir).path
        }
        .distinct()
        .map { it.replace('/', '.') }
  }
}
