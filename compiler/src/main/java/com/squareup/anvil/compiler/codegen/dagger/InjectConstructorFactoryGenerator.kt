package com.squareup.anvil.compiler.codegen.dagger

import com.squareup.anvil.compiler.codegen.CodeGenerator.GeneratedFile
import com.squareup.anvil.compiler.codegen.PrivateCodeGenerator
import com.squareup.anvil.compiler.codegen.addGeneratedByComment
import com.squareup.anvil.compiler.codegen.asArgumentList
import com.squareup.anvil.compiler.codegen.asClassName
import com.squareup.anvil.compiler.codegen.classesAndInnerClasses
import com.squareup.anvil.compiler.codegen.fqNameOrNull
import com.squareup.anvil.compiler.codegen.hasAnnotation
import com.squareup.anvil.compiler.codegen.mapToParameter
import com.squareup.anvil.compiler.codegen.writeToString
import com.squareup.anvil.compiler.generateClassName
import com.squareup.anvil.compiler.injectFqName
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier.OVERRIDE
import com.squareup.kotlinpoet.KModifier.PRIVATE
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.jvm.jvmStatic
import dagger.internal.Factory
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.allConstructors
import java.io.File

internal class InjectConstructorFactoryGenerator : PrivateCodeGenerator() {

  override fun generateCodePrivate(
    codeGenDir: File,
    module: ModuleDescriptor,
    projectFiles: Collection<KtFile>
  ) {
    projectFiles
        .asSequence()
        .flatMap { it.classesAndInnerClasses() }
        .forEach { clazz ->
          clazz.allConstructors
              .singleOrNull { it.hasAnnotation(injectFqName) }
              ?.let {
                generateFactoryClass(codeGenDir, module, clazz, it)
              }
        }
  }

  private fun generateFactoryClass(
    codeGenDir: File,
    module: ModuleDescriptor,
    clazz: KtClassOrObject,
    constructor: KtConstructor<*>
  ): GeneratedFile {
    val packageName = clazz.containingKtFile.packageFqName.asString()
    val className = "${clazz.generateClassName()}_Factory"

    val parameters = constructor.valueParameters.mapToParameter(module)

    val typeParameters = clazz.typeParameterList
      ?.parameters
      ?.mapNotNull { parameter ->
        val extendsBound = parameter.extendsBound?.fqNameOrNull(module)?.asClassName(module)
        if (parameter.fqNameOrNull(module) == null) {
          TypeVariableName(parameter.nameAsSafeName.asString(), listOfNotNull(extendsBound))
        } else {
          null
        }
      }
      ?: emptyList()

    val factoryClass = ClassName(packageName, className)
    val factoryClassParameterized =
      if (typeParameters.isEmpty()) factoryClass else factoryClass.parameterizedBy(typeParameters)

    val classType = clazz.asClassName().let {
      if (typeParameters.isEmpty()) it else it.parameterizedBy(typeParameters)
    }

    val content = FileSpec.builder(packageName, className)
        .apply {
          val canGenerateAnObject = parameters.isEmpty()
          val classBuilder = if (canGenerateAnObject) {
            TypeSpec.objectBuilder(factoryClass)
          } else {
            TypeSpec.classBuilder(factoryClass)
          }
          typeParameters.forEach { classBuilder.addTypeVariable(it) }

          classBuilder
              .addSuperinterface(Factory::class.asClassName().parameterizedBy(classType))
              .apply {
                if (parameters.isNotEmpty()) {
                  primaryConstructor(
                      FunSpec.constructorBuilder()
                          .apply {
                            parameters.forEach { parameter ->
                              addParameter(parameter.name, parameter.providerTypeName)
                            }
                          }
                          .build()
                  )

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
                      .returns(classType)
                      .apply {
                        val argumentList = parameters.asArgumentList(
                            asProvider = true,
                            includeModule = false
                        )

                        addStatement("return newInstance($argumentList)")
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
                              if (typeParameters.isNotEmpty()) {
                                addTypeVariables(typeParameters)
                              }
                              if (canGenerateAnObject) {
                                addStatement("return this")
                              } else {
                                parameters.forEach { parameter ->
                                  addParameter(parameter.name, parameter.providerTypeName)
                                }

                                val argumentList = parameters.asArgumentList(
                                    asProvider = false,
                                    includeModule = false
                                )

                                addStatement(
                                    "return %T($argumentList)",
                                    factoryClassParameterized
                                )
                              }
                            }
                            .returns(factoryClassParameterized)
                            .build()
                    )
                    .addFunction(
                        FunSpec.builder("newInstance")
                            .jvmStatic()
                            .apply {
                              if (typeParameters.isNotEmpty()) {
                                addTypeVariables(typeParameters)
                              }
                              parameters.forEach { parameter ->
                                addParameter(
                                    name = parameter.name,
                                    type = parameter.originalTypeName
                                )
                              }
                              val argumentsWithoutModule = parameters.joinToString { it.name }

                              addStatement("return %T($argumentsWithoutModule)", classType)
                            }
                            .returns(classType)
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
