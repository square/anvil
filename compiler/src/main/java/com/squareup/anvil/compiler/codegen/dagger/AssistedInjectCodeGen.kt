package com.squareup.anvil.compiler.codegen.dagger

import com.google.auto.service.AutoService
import com.squareup.anvil.compiler.api.AnvilApplicabilityChecker
import com.squareup.anvil.compiler.api.AnvilContext
import com.squareup.anvil.compiler.api.CodeGenerator
import com.squareup.anvil.compiler.api.GeneratedFileWithSources
import com.squareup.anvil.compiler.api.createGeneratedFile
import com.squareup.anvil.compiler.assistedInjectFqName
import com.squareup.anvil.compiler.codegen.PrivateCodeGenerator
import com.squareup.anvil.compiler.codegen.injectConstructor
import com.squareup.anvil.compiler.internal.createAnvilSpec
import com.squareup.anvil.compiler.internal.joinSimpleNames
import com.squareup.anvil.compiler.internal.reference.AnvilCompilationExceptionClassReference
import com.squareup.anvil.compiler.internal.reference.ClassReference
import com.squareup.anvil.compiler.internal.reference.MemberFunctionReference
import com.squareup.anvil.compiler.internal.reference.asClassName
import com.squareup.anvil.compiler.internal.reference.classAndInnerClassReferences
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier.PRIVATE
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
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
internal object AssistedInjectCodeGen : AnvilApplicabilityChecker {

  override fun isApplicable(context: AnvilContext) = context.generateFactories

  @AutoService(CodeGenerator::class)
  internal class Embedded : PrivateCodeGenerator() {

    override fun isApplicable(context: AnvilContext) = AssistedInjectCodeGen.isApplicable(context)

    override fun generateCodePrivate(
      codeGenDir: File,
      module: ModuleDescriptor,
      projectFiles: Collection<KtFile>,
    ): List<GeneratedFileWithSources> = projectFiles
      .classAndInnerClassReferences(module)
      .mapNotNull { clazz ->
        clazz.constructors
          .injectConstructor()
          ?.takeIf { it.isAnnotatedWith(assistedInjectFqName) }
          ?.let {
            generateFactoryClass(codeGenDir, clazz, it)
          }
      }
      .toList()

    private fun generateFactoryClass(
      codeGenDir: File,
      clazz: ClassReference.Psi,
      constructor: MemberFunctionReference.Psi,
    ): GeneratedFileWithSources {
      val constructorParameters = constructor.parameters.mapToConstructorParameters()
      val memberInjectParameters = clazz.memberInjectParameters()
      val typeParameters = clazz.typeParameters

      val spec = generateFactoryClass(
        clazz = clazz.asClassName(),
        memberInjectParameters = memberInjectParameters,
        typeParameters = typeParameters.map { it.typeVariableName },
        constructorParameters = constructorParameters,
        onError = { message ->
          throw AnvilCompilationExceptionClassReference(
            message = message,
            classReference = clazz,
          )
        },
      )

      return createGeneratedFile(
        codeGenDir = codeGenDir,
        packageName = spec.packageName,
        fileName = spec.name,
        content = spec.toString(),
        sourceFile = clazz.containingFileAsJavaFile,
      )
    }
  }

  private fun generateFactoryClass(
    clazz: ClassName,
    memberInjectParameters: List<MemberInjectParameter>,
    typeParameters: List<TypeVariableName>,
    constructorParameters: List<ConstructorParameter>,
    onError: (String) -> Nothing,
  ): FileSpec {
    val packageName = clazz.packageName
    val factoryClass = clazz.joinSimpleNames(suffix = "_Factory")

    val parameters = constructorParameters + memberInjectParameters
    val parametersAssisted = parameters.filter { it.isAssisted }
    val parametersNotAssisted = parameters.filterNot { it.isAssisted }

    checkAssistedParametersAreDistinct(parametersAssisted, onError)

    val factoryClassParameterized = factoryClass.optionallyParameterizedByNames(typeParameters)
    val classType = clazz.optionallyParameterizedByNames(typeParameters)

    val spec = FileSpec.createAnvilSpec(packageName, factoryClass.simpleName) {
      TypeSpec.classBuilder(factoryClass)
        .apply {
          addTypeVariables(typeParameters)

          primaryConstructor(
            FunSpec.constructorBuilder()
              .apply {
                parametersNotAssisted.forEach { parameter ->
                  addParameter(parameter.name, parameter.providerTypeName)
                }
              }
              .build(),
          )

          parametersNotAssisted.forEach { parameter ->
            addProperty(
              PropertySpec.builder(parameter.name, parameter.providerTypeName)
                .initializer(parameter.name)
                .addModifiers(PRIVATE)
                .build(),
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
                includeModule = false,
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
            .build(),
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
                    includeModule = false,
                  )

                  addStatement(
                    "return %T($argumentList)",
                    factoryClassParameterized,
                  )
                }
                .returns(factoryClassParameterized)
                .build(),
            )
            .addFunction(
              FunSpec.builder("newInstance")
                .jvmStatic()
                .apply {
                  if (typeParameters.isNotEmpty()) {
                    addTypeVariables(typeParameters)
                  }
                  constructorParameters.forEach { parameter ->
                    addParameter(
                      name = parameter.name,
                      type = parameter.originalTypeName,
                    )
                  }
                  val argumentsWithoutModule = constructorParameters.joinToString { it.name }

                  addStatement("return %T($argumentsWithoutModule)", classType)
                }
                .returns(classType)
                .build(),
            )
            .build()
            .let {
              addType(it)
            }
        }
        .build()
        .let { addType(it) }
    }

    return spec
  }

  private fun checkAssistedParametersAreDistinct(
    parameters: List<Parameter>,
    onError: (String) -> Nothing,
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

    onError(errorMessage)
  }
}
