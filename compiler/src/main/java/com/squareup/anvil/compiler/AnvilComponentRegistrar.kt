package com.squareup.anvil.compiler

import com.google.auto.service.AutoService
import com.squareup.anvil.compiler.codegen.BindingModuleGenerator
import com.squareup.anvil.compiler.codegen.CodeGenerationExtension
import com.squareup.anvil.compiler.codegen.ContributesBindingGenerator
import com.squareup.anvil.compiler.codegen.ContributesToGenerator
import com.squareup.anvil.compiler.codegen.dagger.ComponentDetectorCheck
import com.squareup.anvil.compiler.codegen.dagger.InjectConstructorFactoryGenerator
import com.squareup.anvil.compiler.codegen.dagger.MembersInjectorGenerator
import com.squareup.anvil.compiler.codegen.dagger.ProvidesMethodFactoryGenerator
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.codegen.extensions.ExpressionCodegenExtension
import org.jetbrains.kotlin.com.intellij.mock.MockProject
import org.jetbrains.kotlin.com.intellij.openapi.extensions.LoadingOrder
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

    try {
      // This extension depends on Kotlin 1.4.20 and the code fails to compile with older compiler
      // versions. Anvil will only support the new IR backend with 1.4.20. To avoid compilation
      // errors we only add the source code to this module when IR is enabled. So try to
      // dynamically look up the class name and add the extension when it exists.
      val moduleMergerIr = Class.forName("com.squareup.anvil.compiler.ModuleMergerIr")
          .declaredConstructors
          .single()
          .newInstance(scanner) as IrGenerationExtension

      IrGenerationExtension.registerExtension(
          project, moduleMergerIr
      )
    } catch (ignored: Exception) {
    }
  }

  private fun AnalysisHandlerExtension.Companion.registerExtensionFirst(
    project: MockProject,
    extension: AnalysisHandlerExtension
  ) {
    // This workaround is little concerning, because there is supposed to be a public API for
    // that, but it's not exposed yet. This API is actually part of the IntelliJ platform. The
    // Kotlin compiler repackages and bundles the code. But this one API is missing either because
    // it's an older version or the method is stripped. JetBrains actually suggested in the ticket
    // to use the non-existent API https://youtrack.jetbrains.com/issue/KT-42103
    val analysisHandlerExtensionPoint = project.extensionArea
        .getExtensionPoint(AnalysisHandlerExtension.extensionPointName)
        .let {
          it as? ExtensionPointImpl ?: throw AnvilCompilationException(
              "Expected AnalysisHandlerExtension to be an instance of ExtensionPointImpl. The " +
                  "class is ${it::class.java}"
          )
        }

    ExtensionPointImpl::class.java.declaredMethods
        .first { it.name == "doRegisterExtension" }
        .use {
          it.invoke(analysisHandlerExtensionPoint, extension, LoadingOrder.FIRST, project)
        }
  }
}
