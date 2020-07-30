package com.squareup.anvil.compiler.codegen

import com.squareup.anvil.compiler.ANVIL_MODULE_SUFFIX
import com.squareup.anvil.compiler.AnvilCompilationException
import com.squareup.anvil.compiler.ClassScanner
import com.squareup.anvil.compiler.HINT_BINDING_PACKAGE_PREFIX
import com.squareup.anvil.compiler.MODULE_PACKAGE_PREFIX
import com.squareup.anvil.compiler.annotation
import com.squareup.anvil.compiler.annotationOrNull
import com.squareup.anvil.compiler.boundType
import com.squareup.anvil.compiler.codegen.CodeGenerator.GeneratedFile
import com.squareup.anvil.compiler.contributesBindingFqName
import com.squareup.anvil.compiler.contributesToFqName
import com.squareup.anvil.compiler.daggerBindsFqName
import com.squareup.anvil.compiler.daggerModuleFqName
import com.squareup.anvil.compiler.mergeComponentFqName
import com.squareup.anvil.compiler.mergeModulesFqName
import com.squareup.anvil.compiler.mergeSubcomponentFqName
import com.squareup.anvil.compiler.scope
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.resolveClassByFqName
import org.jetbrains.kotlin.incremental.KotlinLookupLocation
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClassLiteralExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.descriptorUtil.getAllSuperClassifiers
import java.io.File
import java.util.Locale.US

private val supportedFqNames = listOf(
    mergeComponentFqName,
    mergeSubcomponentFqName,
    mergeModulesFqName
)

internal class BindingModuleGenerator(
  private val classScanner: ClassScanner
) : CodeGenerator {

  // Keeps track of for which scopes which files were generated. Usually there is only one file,
  // but technically there can be multiple.
  private val mergedScopes = mutableMapOf<FqName, MutableList<Pair<File, KtClassOrObject>>>()
      .withDefault { mutableListOf() }

  private val contributedBindingClasses = mutableListOf<FqName>()

  override fun generateCode(
    codeGenDir: File,
    module: ModuleDescriptor,
    projectFiles: Collection<KtFile>
  ): Collection<GeneratedFile> {
    // Even though we support multiple rounds the Kotlin compiler let's us generate code only once.
    // It's possible that we use the @ContributesBinding annotation in the module in which we
    // merge components. Remember for which classes a hint was generated and generate a @Binds
    // method for them later.
    findContributedBindingClasses(projectFiles)

    // Generate a Dagger module for each @MergeComponent and friends.
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
              .first().fqNameSafe

          val packageName = generatePackageName(psiClass)
          val className = generateClassName(psiClass)

          val directory = File(codeGenDir, packageName.replace('.', File.separatorChar))
          val file = File(directory, "$className.kt")
          check(file.parentFile.exists() || file.parentFile.mkdirs()) {
            "Could not generate package directory: ${file.parentFile}"
          }

          // We cheat and don't actually write the content to the file. We write the content in the
          // flush() method when we collected all binding methods to avoid two writes.
          check(file.exists() || file.createNewFile()) {
            "Could not create new file: $file"
          }

          val content = daggerModuleContent(
              scope = scope.asString(),
              psiClass = psiClass,
              bindingMethods = emptyList()
          )

          mergedScopes[scope] = mergedScopes.getValue(scope)
              .apply { this += file to psiClass }

          GeneratedFile(file, content)
        }
        .toList()
  }

  @OptIn(ExperimentalStdlibApi::class)
  override fun flush(
    codeGenDir: File,
    module: ModuleDescriptor
  ) {
    mergedScopes.forEach { (scope, daggerModuleFiles) ->
      val contributedBindingsThisModule = contributedBindingClasses
          .mapNotNull { clazz ->
            module.resolveClassByFqName(clazz, NoLookupLocation.FROM_BACKEND)
          }
      val contributedBindingsDependencies = classScanner.findContributedClasses(
          module,
          packageName = HINT_BINDING_PACKAGE_PREFIX,
          annotation = contributesBindingFqName
      )

      val bindingMethods = (contributedBindingsThisModule + contributedBindingsDependencies)
          .filter {
            val annotation = it.annotationOrNull(contributesBindingFqName)
            annotation != null && scope == annotation.scope(module).fqNameSafe
          }
          .map { contributedClass ->
            val annotation = contributedClass.annotation(contributesBindingFqName)
            val boundType = annotation.boundType(module, contributedClass)

            checkExtendsBoundType(type = contributedClass, boundType = boundType)

            val concreteType = contributedClass.fqNameSafe
            val methodName = concreteType
                .asString()
                .split(".")
                .joinToString(separator = "", prefix = "bind") { it.capitalize(US) }

            val paramName = concreteType.shortName()
                .asString()
                .decapitalize(US)

            """
              |
              |  @$daggerBindsFqName abstract fun $methodName(
              |    $paramName: $concreteType
              |  ): ${boundType.fqNameSafe}
            """.trimMargin()
          }

      daggerModuleFiles.forEach { (file, psiClass) ->
        file.writeText(
            daggerModuleContent(
                scope = scope.asString(),
                psiClass = psiClass,
                bindingMethods = bindingMethods
            )
        )
      }
    }
  }

  private fun findContributedBindingClasses(projectFiles: Collection<KtFile>) {
    contributedBindingClasses += projectFiles
        .filter {
          it.packageFqName.asString()
              .startsWith(HINT_BINDING_PACKAGE_PREFIX)
        }
        .flatMap {
          it.findChildrenByClass(KtProperty::class.java)
              .toList()
        }
        .mapNotNull { ktProperty ->
          ((ktProperty.initializer as? KtClassLiteralExpression)
              ?.firstChild as? KtDotQualifiedExpression)
              ?.text
              ?.let { FqName(it) }
        }
  }

  private fun checkExtendsBoundType(
    type: ClassDescriptor,
    boundType: ClassDescriptor
  ) {
    val boundFqName = boundType.fqNameSafe
    val hasSuperType = type.getAllSuperClassifiers()
        .any { it.fqNameSafe == boundFqName }

    if (!hasSuperType) {
      throw AnvilCompilationException(
          classDescriptor = type,
          message = "${type.fqNameSafe} contributes a binding for $boundFqName, but doesn't " +
              "extend this type."
      )
    }
  }

  private fun generateClassName(psiClass: KtClassOrObject): String =
    psiClass.parentsWithSelf
        .filterIsInstance<KtClassOrObject>()
        .toList()
        .reversed()
        .joinToString(separator = "", postfix = ANVIL_MODULE_SUFFIX) {
          it.requireFqName()
              .shortName()
              .asString()
        }

  private fun generatePackageName(psiClass: KtClassOrObject): String =
    "$MODULE_PACKAGE_PREFIX.${psiClass.containingKtFile.packageFqName}"

  private fun daggerModuleContent(
    scope: String,
    psiClass: KtClassOrObject,
    bindingMethods: List<String>
  ): String {
    val packageName = generatePackageName(psiClass)
    val className = generateClassName(psiClass)

    return """
      package $packageName

      @$daggerModuleFqName
      @$contributesToFqName($scope::class)
      abstract class $className {
      ${bindingMethods.joinToString(separator = "\n")}
      }
    """.trimIndent()
  }
}
