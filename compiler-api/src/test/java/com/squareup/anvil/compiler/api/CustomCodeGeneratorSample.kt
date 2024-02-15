package com.squareup.anvil.compiler.api

import com.google.auto.service.AutoService
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

val foo = Unit

@AutoService(CodeGenerator::class)
class CustomCodeGeneratorSample : CodeGenerator {

  override fun isApplicable(context: AnvilContext): Boolean = true

  override fun generateCode(
    codeGenDir: File,
    module: ModuleDescriptor,
    projectFiles: Collection<KtFile>,
  ): Collection<FileWithContent> {
    TODO("Not yet implemented")
  }
}
