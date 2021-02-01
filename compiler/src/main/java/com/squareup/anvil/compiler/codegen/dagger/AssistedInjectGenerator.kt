package com.squareup.anvil.compiler.codegen.dagger

import com.squareup.anvil.compiler.assistedInjectFqName
import com.squareup.anvil.compiler.codegen.CodeGenerator.GeneratedFile
import com.squareup.anvil.compiler.codegen.PrivateCodeGenerator
import com.squareup.anvil.compiler.codegen.asArgumentList
import com.squareup.anvil.compiler.codegen.asClassName
import com.squareup.anvil.compiler.codegen.buildFile
import com.squareup.anvil.compiler.codegen.classesAndInnerClasses
import com.squareup.anvil.compiler.codegen.fqNameOrNull
import com.squareup.anvil.compiler.codegen.injectConstructor
import com.squareup.anvil.compiler.codegen.mapToParameter
import com.squareup.anvil.compiler.generateClassName
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier.PRIVATE
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.jvm.jvmStatic
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

/**
 * Generates the `_Factory` class for a type with an `@AssistedInject` constructor, e.g. for
 * ```
 * class AssistedService @AssistedInject constructor()
 * ```
 * this generator would create
 * ```
 * class AssistedService_Factory { .. }
 * ```
 */
internal class AssistedInjectGenerator : PrivateCodeGenerator() {

  override fun generateCodePrivate(
    codeGenDir: File,
    module: ModuleDescriptor,
    projectFiles: Collection<KtFile>
  ) {
    projectFiles
      .asSequence()
      .flatMap { it.classesAndInnerClasses() }
      .forEach { clazz ->
        clazz.injectConstructor(assistedInjectFqName)
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
    val parametersNotAssisted = parameters.filterNot { it.isAssisted }

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

    val content = FileSpec.buildFile(packageName, className) {

      TypeSpec.classBuilder(factoryClass)
        .apply {
          typeParameters.forEach { addTypeVariable(it) }

          primaryConstructor(
            FunSpec.constructorBuilder()
              .apply {
                parametersNotAssisted.forEach { parameter ->
                  addParameter(parameter.name, parameter.providerTypeName)
                }
              }
              .build()
          )

          parametersNotAssisted.forEach { parameter ->
            addProperty(
              PropertySpec.builder(parameter.name, parameter.providerTypeName)
                .initializer(parameter.name)
                .addModifiers(PRIVATE)
                .build()
            )
          }
        }
        .addFunction(
          FunSpec.builder("get")
            .returns(classType)
            .apply {
              parameters.filter { it.isAssisted }.forEach { parameter ->
                addParameter(parameter.name, parameter.originalTypeName)
              }

              val argumentList = parameters.asArgumentList(
                asProvider = true,
                includeModule = false
              )

              addStatement("return newInstance($argumentList)")
            }
            .build()
        )
        .apply {
          TypeSpec.companionObjectBuilder()
            .addFunction(
              FunSpec.builder("create")
                .jvmStatic()
                .apply {
                  if (typeParameters.isNotEmpty()) {
                    addTypeVariables(typeParameters)
                  }
                  parametersNotAssisted.forEach { parameter ->
                    addParameter(parameter.name, parameter.providerTypeName)
                  }

                  val argumentList = parametersNotAssisted.asArgumentList(
                    asProvider = false,
                    includeModule = false
                  )

                  addStatement(
                    "return %T($argumentList)",
                    factoryClassParameterized
                  )
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
              addType(it)
            }
        }
        .build()
        .let { addType(it) }
    }

    return createGeneratedFile(codeGenDir, packageName, className, content)
  }
}
