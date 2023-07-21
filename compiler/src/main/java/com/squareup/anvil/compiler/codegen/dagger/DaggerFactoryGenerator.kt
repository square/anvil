package com.squareup.anvil.compiler.codegen.dagger

import com.squareup.anvil.compiler.api.AnvilContext
import com.squareup.anvil.compiler.codegen.IS_GENERATED_BY_ANVIL
import com.squareup.anvil.compiler.codegen.PrivateCodeGenerator
import org.jetbrains.kotlin.com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.com.intellij.openapi.vfs.local.CoreLocalVirtualFile
import org.jetbrains.kotlin.com.intellij.testFramework.LightVirtualFile
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

internal abstract class DaggerFactoryGenerator: PrivateCodeGenerator() {
  protected var pathWhitelist = emptyList<String>()

  override fun isApplicable(context: AnvilContext): Boolean {
    pathWhitelist = context.generateFactoriesPathWhitelist

    return context.generateFactories
  }

  abstract fun generateCodeInDaggerFactoryWhitelistedFiles(
    codeGenDir: File,
    module: ModuleDescriptor,
    projectFiles: Collection<KtFile>
  )

  override fun generateCodePrivate(
    codeGenDir: File,
    module: ModuleDescriptor,
    projectFiles: Collection<KtFile>
  ) {
      return generateCodeInDaggerFactoryWhitelistedFiles(
        codeGenDir,
        module,
        projectFiles.filter { ktFile ->
          ktFile.getUserData(IS_GENERATED_BY_ANVIL) == true ||
            pathWhitelist.isEmpty() ||
            pathWhitelist.any { ktFile.virtualFilePath.startsWith(it) }
        }
      )
  }

  private fun VirtualFile.absolutePath(): String {
      return if (this is LightVirtualFile) {
        originalFile.path
      } else if (this is CoreLocalVirtualFile) {
        this.path
      } else {
        throw UnsupportedOperationException("Cannot determine path of $this (${this.javaClass})")
      }
  }
}
