package com.squareup.anvil.compiler.codegen.dagger

import com.google.auto.service.AutoService
import com.squareup.anvil.compiler.api.AnvilContext
import com.squareup.anvil.compiler.api.CodeGenerator
import com.squareup.anvil.compiler.api.GeneratedFile
import com.squareup.anvil.compiler.api.createGeneratedFile
import com.squareup.anvil.compiler.assistedInjectFqName
import com.squareup.anvil.compiler.codegen.PrivateCodeGenerator
import com.squareup.anvil.compiler.codegen.injectConstructor
import com.squareup.anvil.compiler.internal.asClassName
import com.squareup.anvil.compiler.internal.buildFile
import com.squareup.anvil.compiler.internal.reference.AnvilCompilationExceptionClassReference
import com.squareup.anvil.compiler.internal.reference.ClassReference
import com.squareup.anvil.compiler.internal.reference.MemberFunctionReference
import com.squareup.anvil.compiler.internal.reference.asClassName
import com.squareup.anvil.compiler.internal.reference.classAndInnerClassReferences
import com.squareup.anvil.compiler.internal.reference.generateClassName
import com.squareup.anvil.compiler.internal.safePackageString
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier.PRIVATE
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.jvm.jvmStatic
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
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
@AutoService(CodeGenerator::class)
internal class AssistedInjectGenerator : PrivateCodeGenerator() {

  override fun isApplicable(context: AnvilContext) = context.generateFactories

  override fun generateCodePrivate(
    codeGenDir: File,
    module: ModuleDescriptor,
    projectFiles: Collection<KtFile>
  ) {
    projectFiles
      .classAndInnerClassReferences(module)
      .forEach { clazz ->
        clazz.constructors
          .injectConstructor()
          ?.takeIf { it.isAnnotatedWith(assistedInjectFqName) }
          ?.let {
            generateFactoryClass(codeGenDir, clazz, it)
          }
      }
  }

  private fun generateFactoryClass(
    codeGenDir: File,
    clazz: ClassReference.Psi,
    constructor: MemberFunctionReference.Psi
  ): GeneratedFile {
    val packageName = clazz.packageFqName.safePackageString()
    val classIdName = clazz.generateClassName(suffix = "_Factory")
    val className = classIdName.relativeClassName.asString()

    val constructorParameters = constructor.parameters.mapToConstructorParameters()
    val memberInjectParameters = clazz.memberInjectParameters()

    val parameters = constructorParameters + memberInjectParameters
    val parametersAssisted = parameters.filter { it.isAssisted }
    val parametersNotAssisted = parameters.filterNot { it.isAssisted }

    checkAssistedParametersAreDistinct(clazz, parametersAssisted)

    val typeParameters = clazz.typeParameters

    val factoryClass = classIdName.asClassName()
    val factoryClassParameterized = factoryClass.optionallyParameterizedBy(typeParameters)
    val classType = clazz.asClassName().optionallyParameterizedBy(typeParameters)

    val content = FileSpec.buildFile(packageName, className) {
      TypeSpec.classBuilder(factoryClass)
        .apply {
          typeParameters.forEach { addTypeVariable(it.typeVariableName) }

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
              parametersAssisted.forEach { parameter ->
                addParameter(parameter.name, parameter.originalTypeName)
              }

              val argumentList = constructorParameters.asArgumentList(
                asProvider = true,
                includeModule = false
              )

              if (memberInjectParameters.isEmpty()) {
                addStatement("return newInstance($argumentList)")
              } else {
                val instanceName = "instance"
                addStatement("val $instanceName = newInstance($argumentList)")
                addMemberInjection(memberInjectParameters, instanceName)
                addStatement("return $instanceName")
              }
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
                    addTypeVariables(typeParameters.map { it.typeVariableName })
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
                    addTypeVariables(typeParameters.map { it.typeVariableName })
                  }
                  constructorParameters.forEach { parameter ->
                    addParameter(
                      name = parameter.name,
                      type = parameter.originalTypeName
                    )
                  }
                  val argumentsWithoutModule = constructorParameters.joinToString { it.name }

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

  private fun checkAssistedParametersAreDistinct(
    clazz: ClassReference,
    parameters: List<Parameter>
  ) {
    // Parameters are identical, if there types and identifier match.
    val duplicateAssistedParameters = parameters
      .groupBy { it.assistedParameterKey }
      .filterValues { parameterList ->
        parameterList.size > 1
      }

    if (duplicateAssistedParameters.isEmpty()) return

    // Pick the first value in the map and report it as error. Dagger only reports the first
    // error as well. The first value is a list of parameters. Since all parameters in this list
    // are identical, we can simply use the first to construct the error message.
    val parameter = duplicateAssistedParameters.values.first().first()
    val assistedIdentifier = parameter.assistedIdentifier

    val errorMessage = buildString {
      append("@AssistedInject constructor has duplicate @Assisted type: @Assisted")
      if (assistedIdentifier.isNotEmpty()) {
        append("(\"")
        append(assistedIdentifier)
        append("\")")
      }
      append(' ')
      append(parameter.typeName)
    }

    throw AnvilCompilationExceptionClassReference(message = errorMessage, classReference = clazz)
  }
}
