package com.squareup.anvil.compiler

import com.google.auto.service.AutoService
import com.squareup.anvil.compiler.codegen.BindingModuleGenerator
import com.squareup.anvil.compiler.codegen.CodeGenerationExtension
import com.squareup.anvil.compiler.codegen.ContributesBindingGenerator
import com.squareup.anvil.compiler.codegen.ContributesToGenerator
import com.squareup.anvil.compiler.codegen.dagger.ComponentDetectorCheck
import com.squareup.anvil.compiler.codegen.dagger.ContributesAndroidInjectorGenerator
import com.squareup.anvil.compiler.codegen.dagger.InjectConstructorFactoryGenerator
import com.squareup.anvil.compiler.codegen.dagger.MembersInjectorGenerator
import com.squareup.anvil.compiler.codegen.dagger.ProvidesMethodFactoryGenerator
import org.jetbrains.kotlin.codegen.extensions.ExpressionCodegenExtension
import org.jetbrains.kotlin.com.intellij.mock.MockProject
import org.jetbrains.kotlin.com.intellij.openapi.extensions.Extensions
import org.jetbrains.kotlin.com.intellij.openapi.extensions.impl.ExtensionPointImpl
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.resolve.extensions.SyntheticResolveExtension
import org.jetbrains.kotlin.resolve.jvm.extensions.AnalysisHandlerExtension
import java.io.File

/**
 * Entry point for the Anvil Kotlin compiler plugin. It registers several callbacks for the
 * various compilation phases.
 */
@AutoService(ComponentRegistrar::class)
class AnvilComponentRegistrar : ComponentRegistrar {
  override fun registerProjectComponents(
    project: MockProject,
    configuration: CompilerConfiguration
  ) {
    val sourceGenFolder = File(configuration.getNotNull(srcGenDirKey))
    val scanner = ClassScanner()

    val codeGenerators = mutableListOf(
        ContributesToGenerator(),
        ContributesBindingGenerator(),
        BindingModuleGenerator(scanner)
    )

    if (configuration.get(generateDaggerFactoriesKey, false)) {
      codeGenerators += ProvidesMethodFactoryGenerator()
      codeGenerators += InjectConstructorFactoryGenerator()
      codeGenerators += MembersInjectorGenerator()
      codeGenerators += ComponentDetectorCheck()
      codeGenerators += ContributesAndroidInjectorGenerator()
    }

    val codeGenerationExtension = CodeGenerationExtension(
        codeGenDir = sourceGenFolder,
        codeGenerators = codeGenerators
    )

    // It's important to register our extension at the first position. The compiler calls each
    // extension one by one. If an extension returns a result, then the compiler won't call any
    // other extension. That usually happens with Kapt in the stub generating task.
    //
    // It's not dangerous for our extension to run first, because we generate code, restart the
    // analysis phase and then don't return a result anymore. That means the next extension can
    // take over. If we wouldn't do this and any other extension won't let our's run, then we
    // couldn't generate any code.
    AnalysisHandlerExtension.registerExtensionFirst(
        project, codeGenerationExtension
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
      // for inner classes where they convert the given class to a String "a.b.C.Inner" and then
      // try to remove "a.b.C$Inner". Good times! Workaround is below.
      analysisHandlerExtensionPoint.unregisterExtension(it::class.java)
    }

    if (analysisHandlerExtensionPoint.hasAnyExtensions() &&
        analysisHandlerExtensionPoint is ExtensionPointImpl<AnalysisHandlerExtension>
    ) {
      AnalysisHandlerExtension.getInstances(project)
          .forEach {
            analysisHandlerExtensionPoint.unregisterExtensionFixed(it::class.java)
          }
    }

    check(!analysisHandlerExtensionPoint.hasAnyExtensions()) {
      "There are still registered extensions."
    }

    AnalysisHandlerExtension.registerExtension(project, extension)
    registeredExtensions.forEach { AnalysisHandlerExtension.registerExtension(project, it) }
  }

  private fun <T : AnalysisHandlerExtension> ExtensionPointImpl<T>.unregisterExtensionFixed(
    extensionClass: Class<out T>
  ) {
    // The bug is that they use "extensionClass.canonicalName".
    val classNameToUnregister = extensionClass.name
    unregisterExtensions({ className, _ ->
      classNameToUnregister != className
    }, true)
  }
}
