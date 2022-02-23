package com.squareup.anvil.compiler.codegen.dagger

import com.google.auto.service.AutoService
import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.api.AnvilContext
import com.squareup.anvil.compiler.api.CodeGenerator
import com.squareup.anvil.compiler.api.GeneratedFile
import com.squareup.anvil.compiler.api.createGeneratedFile
import com.squareup.anvil.compiler.assistedFactoryFqName
import com.squareup.anvil.compiler.assistedFqName
import com.squareup.anvil.compiler.assistedInjectFqName
import com.squareup.anvil.compiler.codegen.PrivateCodeGenerator
import com.squareup.anvil.compiler.codegen.dagger.AssistedFactoryGenerator.AssistedFactoryFunction.Companion.toAssistedFactoryFunction
import com.squareup.anvil.compiler.codegen.dagger.AssistedFactoryGenerator.AssistedParameterKey.Companion.toAssistedParameterKey
import com.squareup.anvil.compiler.internal.asClassName
import com.squareup.anvil.compiler.internal.buildFile
import com.squareup.anvil.compiler.internal.reference.AnvilCompilationExceptionClassReference
import com.squareup.anvil.compiler.internal.reference.AnvilCompilationExceptionFunctionReference
import com.squareup.anvil.compiler.internal.reference.ClassReference
import com.squareup.anvil.compiler.internal.reference.FunctionReference
import com.squareup.anvil.compiler.internal.reference.ParameterReference
import com.squareup.anvil.compiler.internal.reference.Visibility
import com.squareup.anvil.compiler.internal.reference.allSuperTypeClassReferences
import com.squareup.anvil.compiler.internal.reference.argumentAt
import com.squareup.anvil.compiler.internal.reference.asClassName
import com.squareup.anvil.compiler.internal.reference.classAndInnerClassReferences
import com.squareup.anvil.compiler.internal.reference.generateClassName
import com.squareup.anvil.compiler.internal.safePackageString
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier.OVERRIDE
import com.squareup.kotlinpoet.KModifier.PRIVATE
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.jvm.jvmStatic
import dagger.internal.InstanceFactory
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtFile
import java.io.File
import javax.inject.Provider

@AutoService(CodeGenerator::class)
internal class AssistedFactoryGenerator : PrivateCodeGenerator() {

  override fun isApplicable(context: AnvilContext) = context.generateFactories

  override fun generateCodePrivate(
    codeGenDir: File,
    module: ModuleDescriptor,
    projectFiles: Collection<KtFile>
  ) {
    projectFiles
      .classAndInnerClassReferences(module)
      .filter { it.isAnnotatedWith(assistedFactoryFqName) }
      .forEach { clazz ->
        generateFactoryClass(codeGenDir, module, clazz)
      }
  }

  private fun generateFactoryClass(
    codeGenDir: File,
    module: ModuleDescriptor,
    clazz: ClassReference.Psi
  ): GeneratedFile {
    val packageName = clazz.packageFqName.safePackageString()
    val delegateFactoryName = "delegateFactory"

    val function = clazz.requireSingleAbstractFunction()

    val returnType = try {
      function.function.resolveGenericReturnType(clazz)
    } catch (e: AnvilCompilationException) {
      // Catch the exception and throw the same error that Dagger would.
      throw AnvilCompilationExceptionFunctionReference(
        message = "Invalid return type: ${clazz.fqName}. An assisted factory's " +
          "abstract method must return a type with an @AssistedInject-annotated constructor.",
        functionReference = function.function,
        cause = e
      )
    }

    // The return type of the function must have an @AssistedInject constructor.
    val constructor = returnType
      .constructors
      .singleOrNull { it.isAnnotatedWith(assistedInjectFqName) }
      ?: throw AnvilCompilationExceptionClassReference(
        message = "Invalid return type: ${returnType.fqName}. An assisted factory's abstract " +
          "method must return a type with an @AssistedInject-annotated constructor.",
        classReference = clazz
      )

    val functionParameters = function.parameterKeys
    val assistedParameters = constructor.parameters.filter { parameter ->
      parameter.annotations.any { it.fqName == assistedFqName }
    }

    // Check that the parameters of the function match the @Assisted parameters of the constructor.
    if (assistedParameters.size != functionParameters.size) {
      throw AnvilCompilationExceptionClassReference(
        message = "The parameters in the factory method must match the @Assisted parameters in " +
          "${returnType.fqName}.",
        classReference = clazz
      )
    }

    // Compute for each parameter its key.
    val functionParameterKeys = function.parameterKeys
    val assistedParameterKeys = assistedParameters.map { it.toAssistedParameterKey(clazz) }

    // The factory function may not have two or more parameters with the same key.
    val duplicateKeys = functionParameterKeys
      .groupBy { it.key }
      .filter { it.value.size > 1 }
      .values
      .flatten()

    if (duplicateKeys.isNotEmpty()) {
      // Complain about the first duplicate key that occurs, similar to Dagger.
      val key = functionParameterKeys.first { it in duplicateKeys }

      throw AnvilCompilationExceptionClassReference(
        message = buildString {
          append("@AssistedFactory method has duplicate @Assisted types: ")
          if (key.identifier.isNotEmpty()) {
            append("@Assisted(\"${key.identifier}\") ")
          }
          append(key.typeName)
        },
        classReference = clazz
      )
    }

    // Check that for each parameter of the factory function there is a parameter with the same
    // key in the @AssistedInject constructor.
    val notMatchingKeys = (functionParameterKeys + assistedParameterKeys)
      .groupBy { it.key }
      .filter { it.value.size == 1 }
      .values
      .flatten()

    if (notMatchingKeys.isNotEmpty()) {
      throw AnvilCompilationExceptionClassReference(
        message = "The parameters in the factory method must match the @Assisted parameters in " +
          "${returnType.fqName}.",
        classReference = clazz
      )
    }

    val typeParameters = clazz.clazz.typeVariableNames(module)

    fun ClassName.parameterized(): TypeName {
      return if (typeParameters.isEmpty()) this else parameterizedBy(typeParameters)
    }

    val generatedFactoryTypeName = returnType.generateClassName(suffix = "_Factory")
      .asClassName()
      .parameterized()

    val baseFactoryTypeName = clazz.asClassName().parameterized()

    val returnTypeName = returnType.asClassName().parameterized()

    val implClassName = clazz.generateClassName(suffix = "_Impl").asClassName()
    val implParameterizedTypeName = implClassName.parameterized()
    val functionName = function.function.name
    val baseFactoryIsInterface = clazz.isInterface()
    val functionParameterPairs = function.parameterPairs

    val content = FileSpec.buildFile(packageName, implClassName.simpleName) {
      TypeSpec.classBuilder(implClassName)
        .apply {
          typeParameters.forEach { addTypeVariable(it) }

          if (baseFactoryIsInterface) {
            addSuperinterface(baseFactoryTypeName)
          } else {
            superclass(baseFactoryTypeName)
          }

          primaryConstructor(
            FunSpec.constructorBuilder()
              .addParameter(delegateFactoryName, generatedFactoryTypeName)
              .build()
          )

          addProperty(
            PropertySpec.builder(delegateFactoryName, generatedFactoryTypeName)
              .initializer(delegateFactoryName)
              .addModifiers(PRIVATE)
              .build()
          )
        }
        .addFunction(
          FunSpec.builder(functionName)
            .addModifiers(OVERRIDE)
            .returns(returnTypeName)
            .apply {
              functionParameterPairs.forEach { parameter ->
                addParameter(parameter.first.name, parameter.second)
              }

              // We call the @AssistedInject constructor. Therefore, find for each assisted
              // parameter the function parameter where the keys match.
              val argumentList = assistedParameterKeys.joinToString { assistedParameterKey ->
                val functionIndex = functionParameterKeys.indexOfFirst {
                  it.key == assistedParameterKey.key
                }
                check(functionIndex >= 0) {
                  // Sanity check, this should not happen with the noMatchingKeys list check above.
                  "Unexpected assistedIndex."
                }

                functionParameterPairs[functionIndex].first.name
              }

              addStatement("return $delegateFactoryName.get($argumentList)")
            }
            .build()
        )
        .apply {
          TypeSpec.companionObjectBuilder()
            .addFunction(
              FunSpec.builder("create")
                .jvmStatic()
                .addTypeVariables(typeParameters)
                .addParameter(delegateFactoryName, generatedFactoryTypeName)
                .returns(Provider::class.asClassName().parameterizedBy(baseFactoryTypeName))
                .addStatement(
                  "return %T.create(%T($delegateFactoryName))",
                  InstanceFactory::class,
                  implParameterizedTypeName
                )
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

    return createGeneratedFile(codeGenDir, packageName, implClassName.simpleName, content)
  }

  private fun ClassReference.Psi.requireSingleAbstractFunction(): AssistedFactoryFunction {
    // `clazz` must be first in the list because of `distinctBy { ... }`, which keeps the first
    // matched element. If the function's inherited, it can be overridden as well. Prioritizing
    // the version from the file we're parsing ensures the correct variance of the referenced types.
    val assistedFunctions = allSuperTypeClassReferences(includeSelf = true)
      .distinctBy { it.fqName }
      .flatMap { clazz ->
        clazz.functions
          .filter {
            it.isAbstract() &&
              (it.visibility() == Visibility.PUBLIC || it.visibility() == Visibility.PROTECTED)
          }
      }
      .distinctBy { it.name }
      .map { it.toAssistedFactoryFunction(this) }
      .toList()

    // Check for exact number of functions.
    return when (assistedFunctions.size) {
      0 -> throw AnvilCompilationExceptionClassReference(
        message = "The @AssistedFactory-annotated type is missing an abstract, non-default " +
          "method whose return type matches the assisted injection type.",
        classReference = this
      )
      1 -> assistedFunctions[0]
      else -> {
        val foundFunctions = assistedFunctions
          .sortedBy { it.function.name }
          .joinToString { func ->
            "${func.function.name}(${func.parameterPairs.map { it.first.name }})"
          }
        throw AnvilCompilationExceptionClassReference(
          message = "The @AssistedFactory-annotated type should contain a single abstract, " +
            "non-default method but found multiple: [$foundFunctions]",
          classReference = this
        )
      }
    }
  }

  /**
   * Represents a parsed function in an `@AssistedInject.Factory`-annotated interface.
   */
  private data class AssistedFactoryFunction(
    val function: FunctionReference,
    val parameterKeys: List<AssistedParameterKey>,
    /**
     * Pair of parameter reference to parameter type.
     */
    val parameterPairs: List<Pair<ParameterReference, TypeName>>
  ) {
    companion object {
      fun FunctionReference.toAssistedFactoryFunction(
        factoryClass: ClassReference.Psi
      ): AssistedFactoryFunction {
        return AssistedFactoryFunction(
          function = this,
          parameterKeys = parameters.map { it.toAssistedParameterKey(factoryClass) },
          parameterPairs = parameters.map { it to it.resolveTypeName(factoryClass) }
        )
      }
    }
  }

  // Dagger matches parameters of the factory function with the parameters of the @AssistedInject
  // constructor through a key. Initially, they used the order of parameters, but that has changed.
  // The key is a combination of the type and identifier (value parameter) of the
  // @Assisted("...") annotation. For each parameter the key must be unique.
  private data class AssistedParameterKey(
    val typeName: TypeName,
    val identifier: String
  ) {

    // Key value is similar to a hash function.  There used to be a special case for KotlinTypes
    // which were parameterized, but this is now handled by KotlinPoet's TypeName.
    // `MyType<String>` and `MyType<Int>` now generate different hashCodes.
    val key: Int = identifier.hashCode() * 31 + typeName.hashCode()

    companion object {
      fun ParameterReference.toAssistedParameterKey(
        factoryClass: ClassReference.Psi
      ): AssistedParameterKey {
        return AssistedParameterKey(
          typeName = resolveTypeName(factoryClass),
          identifier = annotations
            .singleOrNull { it.fqName == assistedFqName }
            ?.let { annotation ->
              annotation.argumentAt("value", index = 0)?.value<String>()
            }
            .orEmpty()
        )
      }
    }
  }
}
