package com.squareup.hephaestus.compiler

import com.google.auto.service.AutoService
import org.jetbrains.kotlin.codegen.extensions.ExpressionCodegenExtension
import org.jetbrains.kotlin.com.intellij.mock.MockProject
import org.jetbrains.kotlin.com.intellij.openapi.extensions.Extensions
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
    val scanner = ClassScanner()
    val sourceGenFolder = File(configuration.getNotNull(srcGenDirKey))

    // It's important to register our extension at the first position. The compiler calls each
    // extension one by one. If an extension returns a result, then the compiler won't call any
    // other extension. That usually happens with Kapt in the stub generating task.
    //
    // It's not dangerous for our extension to run first, because we generate code, restart the
    // analysis phase and then don't return a result anymore. That means the next extension can
    // take over. If we wouldn't do this and any other extension won't let our's run, then we
    // couldn't generate any code.
    AnalysisHandlerExtension.registerExtensionFirst(
        project, ContributeToGenerator(sourceGenFolder)
    )

    SyntheticResolveExtension.registerExtension(
        project, InterfaceMerger(scanner)
    )
    ExpressionCodegenExtension.registerExtension(
        project, ModuleMerger(scanner)
    )
  }

  private fun AnalysisHandlerExtension.Companion.registerExtensionFirst(
    project: MockProject,
    extension: AnalysisHandlerExtension
  ) {
    @Suppress("DEPRECATION")
    val analysisHandlerExtensionPoint = Extensions.getArea(project)
        .getExtensionPoint(AnalysisHandlerExtension.extensionPointName)

    val registeredExtensions = AnalysisHandlerExtension.getInstances(project)
    registeredExtensions.forEach {
      // This doesn't work reliably, but that's the best we can do with public APIs. There's a bug
      // for inner classes where they convert the given class to "a.b.C.Inner" and then try to
      // remove "a.b.C$Inner". Good times!
      analysisHandlerExtensionPoint.unregisterExtension(it::class.java)
    }

    val removedExtensions = registeredExtensions - AnalysisHandlerExtension.getInstances(project)

    AnalysisHandlerExtension.registerExtension(project, extension)
    removedExtensions.forEach { AnalysisHandlerExtension.registerExtension(project, it) }
  }
}
