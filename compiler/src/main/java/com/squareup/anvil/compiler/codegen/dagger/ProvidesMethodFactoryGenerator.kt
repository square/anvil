package com.squareup.anvil.compiler.codegen.dagger

import com.squareup.anvil.compiler.AnvilCompilationException
import com.squareup.anvil.compiler.codegen.CodeGenerator.GeneratedFile
import com.squareup.anvil.compiler.codegen.PrivateCodeGenerator
import com.squareup.anvil.compiler.codegen.addGeneratedByComment
import com.squareup.anvil.compiler.codegen.asArgumentList
import com.squareup.anvil.compiler.codegen.asClassName
import com.squareup.anvil.compiler.codegen.classesAndInnerClasses
import com.squareup.anvil.compiler.codegen.functions
import com.squareup.anvil.compiler.codegen.hasAnnotation
import com.squareup.anvil.compiler.codegen.isNullable
import com.squareup.anvil.compiler.codegen.mapToParameter
import com.squareup.anvil.compiler.codegen.requireFqName
import com.squareup.anvil.compiler.codegen.requireTypeName
import com.squareup.anvil.compiler.codegen.requireTypeReference
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

internal class ProvidesMethodFactoryGenerator : PrivateCodeGenerator() {

  override fun generateCodePrivate(
    codeGenDir: File,
    module: ModuleDescriptor,
    projectFiles: Collection<KtFile>
  ) {
    projectFiles
        .asSequence()
        .flatMap { it.classesAndInnerClasses() }
        .filter { it.hasAnnotation(daggerModuleFqName) }
        .forEach { clazz ->
          clazz
              .functions(includeCompanionObjects = true)
              .asSequence()
              .filter { it.hasAnnotation(daggerProvidesFqName) }
              .also { functions ->
                // Check for duplicate function names.
                val duplicateFunctions = functions
                    .groupBy { it.requireFqName() }
                    .filterValues { it.size > 1 }

                if (duplicateFunctions.isNotEmpty()) {
                  throw AnvilCompilationException(
                      element = clazz,
                      message = "Cannot have more than one binding method with the same name in " +
                          "a single module: ${duplicateFunctions.keys.joinToString()}"
                  )
                }
              }
              .forEach { function ->
                generateFactoryClass(codeGenDir, module, clazz, function)
              }
        }
  }

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

    val returnType = function.requireTypeReference().requireTypeName(module)
        .withJvmSuppressWildcardsIfNeeded(function)
    val returnTypeIsNullable = function.typeReference?.isNullable() ?: false

    val factoryClass = ClassName(packageName, className)
    val moduleClass = clazz.asClassName()

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
