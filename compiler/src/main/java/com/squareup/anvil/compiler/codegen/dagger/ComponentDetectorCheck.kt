package com.squareup.anvil.compiler.codegen.dagger

import com.squareup.anvil.compiler.AnvilCompilationException
import com.squareup.anvil.compiler.codegen.PrivateCodeGenerator
import com.squareup.anvil.compiler.codegen.classesAndInnerClasses
import com.squareup.anvil.compiler.codegen.hasAnnotation
import com.squareup.anvil.compiler.daggerComponentFqName
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

internal class ComponentDetectorCheck : PrivateCodeGenerator() {

  override fun generateCodePrivate(
    codeGenDir: File,
    module: ModuleDescriptor,
    projectFiles: Collection<KtFile>
  ) {
    val component = projectFiles
        .asSequence()
        .flatMap { it.classesAndInnerClasses() }
        .firstOrNull { it.hasAnnotation(daggerComponentFqName) }

    if (component != null) {
      throw AnvilCompilationException(
          message = "Anvil cannot generate the code for Dagger components or subcomponents. In " +
              "these cases the Dagger annotation processor is required. Enabling the Dagger " +
              "annotation processor and turning on Anvil to generate Dagger factories is " +
              "redundant. Set 'generateDaggerFactories' to false.",
          element = component
      )
    }
  }
}
