package com.squareup.anvil.compiler.codegen

import com.squareup.anvil.compiler.AnvilCompilationException
import com.squareup.anvil.compiler.MODULE_PACKAGE_PREFIX
import com.squareup.anvil.compiler.annotationOrNull
import com.squareup.anvil.compiler.codegen.CodeGenerator.GeneratedFile
import com.squareup.anvil.compiler.contributesToFqName
import com.squareup.anvil.compiler.daggerModuleFqName
import com.squareup.anvil.compiler.mergeComponentFqName
import com.squareup.anvil.compiler.mergeModulesFqName
import com.squareup.anvil.compiler.mergeSubcomponentFqName
import com.squareup.anvil.compiler.scope
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.resolveClassByFqName
import org.jetbrains.kotlin.incremental.KotlinLookupLocation
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import java.io.File

private val supportedFqNames = listOf(
    mergeComponentFqName,
    mergeSubcomponentFqName,
    mergeModulesFqName
)

internal class BindingModuleGenerator : CodeGenerator {

  override fun generateCode(
    codeGenDir: File,
    module: ModuleDescriptor,
    projectFiles: Collection<KtFile>
  ): Collection<GeneratedFile> {
    return projectFiles.asSequence()
        .flatMap { it.classesAndInnerClasses() }
        .filter { psiClass -> supportedFqNames.any { psiClass.hasAnnotation(it) } }
        .map { psiClass ->
          val classDescriptor =
            module.resolveClassByFqName(psiClass.requireFqName(), KotlinLookupLocation(psiClass))
                ?: throw AnvilCompilationException(
                    "Couldn't resolve class for PSI element.", element = psiClass
                )

          // The annotation must be present due to the filter above.
          val scope = supportedFqNames
              .mapNotNull {
                classDescriptor.annotationOrNull(it)
                    ?.scope(module)
              }
              .first()

          val packageName =
            "$MODULE_PACKAGE_PREFIX.${psiClass.containingKtFile.packageFqName.asString()}"

          val className = generateClassName(psiClass)

          val directory = File(codeGenDir, packageName.replace('.', File.separatorChar))
          val file = File(directory, "${className}.kt")
          check(file.parentFile.exists() || file.parentFile.mkdirs()) {
            "Could not generate package directory: $this"
          }

          val content = """
              package $packageName

              @$daggerModuleFqName
              @$contributesToFqName(${scope.fqNameSafe}::class)
              abstract class $className { 
              }
          """.trimIndent()
          file.writeText(content)

          GeneratedFile(file, content)
        }
        .toList()
  }

  private fun generateClassName(psiClass: KtClassOrObject): String {
    return psiClass.parentsWithSelf
        .filterIsInstance<KtClassOrObject>()
        .toList()
        .reversed()
        .joinToString(separator = "", postfix = "AnvilModule") {
          it.requireFqName()
              .shortName()
              .asString()
        }
  }
}
