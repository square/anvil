package com.squareup.anvil.compiler.codegen.dagger

import com.google.auto.service.AutoService
import com.squareup.anvil.compiler.api.AnvilContext
import com.squareup.anvil.compiler.api.CodeGenerator
import com.squareup.anvil.compiler.codegen.PrivateCodeGenerator
import com.squareup.anvil.compiler.daggerComponentFqName
import com.squareup.anvil.compiler.internal.reference.AnvilCompilationExceptionClassReference
import com.squareup.anvil.compiler.internal.reference.classAndInnerClassReferences
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

@AutoService(CodeGenerator::class)
internal class ComponentDetectorCheck : DaggerFactoryGenerator() {
  override fun generateCodeInDaggerFactoryWhitelistedFiles(
    codeGenDir: File,
    module: ModuleDescriptor,
    projectFiles: Collection<KtFile>
  ) {
    val component = projectFiles
      .classAndInnerClassReferences(module)
      .firstOrNull { it.isAnnotatedWith(daggerComponentFqName) }

    if (component != null) {
      throw AnvilCompilationExceptionClassReference(
        message = "Anvil cannot generate the code for Dagger components or subcomponents. In " +
          "these cases the Dagger annotation processor is required. Enabling the Dagger " +
          "annotation processor and turning on Anvil to generate Dagger factories is " +
          "redundant. Set 'generateDaggerFactories' to false.",
        classReference = component
      )
    }
  }
}
