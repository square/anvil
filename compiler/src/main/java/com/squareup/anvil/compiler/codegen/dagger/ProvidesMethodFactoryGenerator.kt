package com.squareup.anvil.compiler.codegen.dagger

import com.squareup.anvil.compiler.codegen.CodeGenerator
import com.squareup.anvil.compiler.codegen.CodeGenerator.GeneratedFile
import com.squareup.anvil.compiler.codegen.addGeneratedByComment
import com.squareup.anvil.compiler.codegen.asArgumentList
import com.squareup.anvil.compiler.codegen.asTypeName
import com.squareup.anvil.compiler.codegen.classesAndInnerClasses
import com.squareup.anvil.compiler.codegen.functions
import com.squareup.anvil.compiler.codegen.hasAnnotation
import com.squareup.anvil.compiler.codegen.isNullable
import com.squareup.anvil.compiler.codegen.mapToParameter
import com.squareup.anvil.compiler.codegen.replaceImports
import com.squareup.anvil.compiler.codegen.requireFqName
import com.squareup.anvil.compiler.codegen.requireTypeName
import com.squareup.anvil.compiler.codegen.withJvmSuppressWildcardsIfNeeded
import com.squareup.anvil.compiler.codegen.writeToString
import com.squareup.anvil.compiler.daggerModuleFqName
import com.squareup.anvil.compiler.daggerProvidesFqName
import com.squareup.anvil.compiler.generateClassName
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier.OVERRIDE
import com.squareup.kotlinpoet.KModifier.PRIVATE
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.jvm.jvmStatic
import dagger.internal.Factory
import dagger.internal.Preconditions
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.psiUtil.parents
import java.io.File
import java.util.Locale.US

internal class ProvidesMethodFactoryGenerator : CodeGenerator {
  override fun generateCode(
    codeGenDir: File,
    module: ModuleDescriptor,
    projectFiles: Collection<KtFile>
  ): Collection<GeneratedFile> {
    return projectFiles
        .asSequence()
        .flatMap { it.classesAndInnerClasses() }
        .filter { it.hasAnnotation(daggerModuleFqName) }
        .flatMap { clazz ->
          clazz
              .functions(includeCompanionObjects = true)
              .asSequence()
              .filter { it.hasAnnotation(daggerProvidesFqName) }
              .map { function ->
                generateFactoryClass(codeGenDir, module, clazz, function)
              }
        }
        .toList()
  }

  @OptIn(ExperimentalStdlibApi::class)
  private fun generateFactoryClass(
    codeGenDir: File,
    module: ModuleDescriptor,
    clazz: KtClassOrObject,
    function: KtNamedFunction
  ): GeneratedFile {
    val isCompanionObject = function.parents
        .filterIsInstance<KtObjectDeclaration>()
        .firstOrNull()
        ?.isCompanion()
        ?: false
    val isObject = isCompanionObject || clazz is KtObjectDeclaration

    val packageName = clazz.containingKtFile.packageFqName.asString()
    val className = "${clazz.generateClassName()}_" +
        (if (isCompanionObject) "Companion_" else "") +
        "${function.requireFqName().shortName().asString().capitalize(US)}Factory"
    val functionName = function.nameAsSafeName.asString()

    val parameters = function.valueParameters.mapToParameter(module)

    // This is a little hacky. The typeElement could be "String", "Abc", etc., but also a generic
    // type like "List<String>". We simply copy this literal as return type into our generated
    // code. Kotlinpoet will add an import for this literal like "import String", which we later
    // will remove again.
    //
    // This solution is a lot easier than trying to resolve all FqNames for each type.
    val returnType = function.requireTypeName(module)
        .withJvmSuppressWildcardsIfNeeded(function)
    val returnTypeIsNullable = function.typeReference?.isNullable() ?: false

    val factoryClass = ClassName(packageName, className)
    val moduleClass = clazz.asTypeName()

    val content = FileSpec.builder(packageName, className)
        .apply {
          val canGenerateAnObject = isObject && parameters.isEmpty()
          val classBuilder = if (canGenerateAnObject) {
            TypeSpec.objectBuilder(factoryClass)
          } else {
            TypeSpec.classBuilder(factoryClass)
          }

          classBuilder.addSuperinterface(Factory::class.asClassName().parameterizedBy(returnType))
              .apply {
                if (!canGenerateAnObject) {
                  primaryConstructor(
                      FunSpec.constructorBuilder()
                          .apply {
                            if (!isObject) {
                              addParameter("module", moduleClass)
                            }
                            parameters.forEach { parameter ->
                              addParameter(parameter.name, parameter.providerTypeName)
                            }
                          }
                          .build()
                  )

                  if (!isObject) {
                    addProperty(
                        PropertySpec.builder("module", moduleClass)
                            .initializer("module")
                            .addModifiers(PRIVATE)
                            .build()
                    )
                  }

                  parameters.forEach { parameter ->
                    addProperty(
                        PropertySpec.builder(parameter.name, parameter.providerTypeName)
                            .initializer(parameter.name)
                            .addModifiers(PRIVATE)
                            .build()
                    )
                  }
                }
              }
              .addFunction(
                  FunSpec.builder("get")
                      .addModifiers(OVERRIDE)
                      .returns(returnType)
                      .apply {
                        val argumentList = parameters.asArgumentList(
                            asProvider = true,
                            includeModule = !isObject
                        )
                        addStatement("return $functionName($argumentList)")
                      }
                      .build()
              )
              .apply {
                val builder = if (canGenerateAnObject) this else TypeSpec.companionObjectBuilder()
                builder
                    .addFunction(
                        FunSpec.builder("create")
                            .jvmStatic()
                            .apply {
                              if (canGenerateAnObject) {
                                addStatement("return this")
                              } else {
                                if (!isObject) {
                                  addParameter("module", moduleClass)
                                }
                                parameters.forEach { parameter ->
                                  addParameter(parameter.name, parameter.providerTypeName)
                                }

                                val argumentList = parameters.asArgumentList(
                                    asProvider = false,
                                    includeModule = !isObject
                                )

                                addStatement("return %T($argumentList)", factoryClass)
                              }
                            }
                            .returns(factoryClass)
                            .build()
                    )
                    .addFunction(
                        FunSpec.builder(functionName)
                            .jvmStatic()
                            .apply {
                              if (!isObject) {
                                addParameter("module", moduleClass)
                              }

                              parameters.forEach { parameter ->
                                addParameter(
                                    name = parameter.name,
                                    type = parameter.originalTypeName
                                )
                              }
                              val argumentsWithoutModule = parameters.joinToString { it.name }

                              when {
                                isObject && returnTypeIsNullable ->
                                  addStatement(
                                      "return %T.$functionName($argumentsWithoutModule)",
                                      moduleClass
                                  )
                                isObject && !returnTypeIsNullable ->
                                  addStatement(
                                      "return %T.checkNotNull(%T.$functionName" +
                                          "($argumentsWithoutModule), %S)",
                                      Preconditions::class,
                                      moduleClass,
                                      "Cannot return null from a non-@Nullable @Provides method"
                                  )
                                !isObject && returnTypeIsNullable ->
                                  addStatement(
                                      "return module.$functionName($argumentsWithoutModule)"
                                  )
                                !isObject && !returnTypeIsNullable ->
                                  addStatement(
                                      "return %T.checkNotNull(module.$functionName" +
                                          "($argumentsWithoutModule), %S)",
                                      Preconditions::class,
                                      "Cannot return null from a non-@Nullable @Provides method"
                                  )
                              }
                            }
                            .returns(returnType)
                            .build()
                    )
                    .build()
                    .let {
                      if (!canGenerateAnObject) {
                        addType(it)
                      }
                    }
              }
              .build()
              .let { addType(it) }
        }
        .build()
        .writeToString()
        .replaceImports(clazz)
        .addGeneratedByComment()

    val directory = File(codeGenDir, packageName.replace('.', File.separatorChar))
    val file = File(directory, "$className.kt")
    check(file.parentFile.exists() || file.parentFile.mkdirs()) {
      "Could not generate package directory: ${file.parentFile}"
    }
    file.writeText(content)

    return GeneratedFile(file, content)
  }
}
