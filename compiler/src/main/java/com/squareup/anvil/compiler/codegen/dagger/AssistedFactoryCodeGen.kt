package com.squareup.anvil.compiler.codegen.dagger

import com.google.auto.service.AutoService
import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunction
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Visibility.PROTECTED
import com.google.devtools.ksp.symbol.Visibility.PUBLIC
import com.squareup.anvil.compiler.api.AnvilApplicabilityChecker
import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.api.AnvilContext
import com.squareup.anvil.compiler.api.CodeGenerator
import com.squareup.anvil.compiler.api.GeneratedFileWithSources
import com.squareup.anvil.compiler.api.createGeneratedFile
import com.squareup.anvil.compiler.assistedFactoryFqName
import com.squareup.anvil.compiler.assistedFqName
import com.squareup.anvil.compiler.assistedInjectFqName
import com.squareup.anvil.compiler.codegen.PrivateCodeGenerator
import com.squareup.anvil.compiler.codegen.dagger.AssistedFactoryCodeGen.AssistedParameterKey.Companion.toAssistedParameterKey
import com.squareup.anvil.compiler.codegen.dagger.AssistedFactoryCodeGen.Embedded.AssistedFactoryFunction.Companion.toAssistedFactoryFunction
import com.squareup.anvil.compiler.codegen.dagger.AssistedFactoryCodeGen.KspGenerator.AssistedFactoryFunction.Companion.toAssistedFactoryFunction
import com.squareup.anvil.compiler.codegen.ksp.AnvilSymbolProcessor
import com.squareup.anvil.compiler.codegen.ksp.AnvilSymbolProcessorProvider
import com.squareup.anvil.compiler.codegen.ksp.KspAnvilException
import com.squareup.anvil.compiler.codegen.ksp.isAnnotationPresent
import com.squareup.anvil.compiler.codegen.ksp.isInterface
import com.squareup.anvil.compiler.codegen.ksp.resolveKSClassDeclaration
import com.squareup.anvil.compiler.internal.createAnvilSpec
import com.squareup.anvil.compiler.internal.joinSimpleNames
import com.squareup.anvil.compiler.internal.reference.AnvilCompilationExceptionClassReference
import com.squareup.anvil.compiler.internal.reference.AnvilCompilationExceptionFunctionReference
import com.squareup.anvil.compiler.internal.reference.ClassReference
import com.squareup.anvil.compiler.internal.reference.MemberFunctionReference
import com.squareup.anvil.compiler.internal.reference.ParameterReference
import com.squareup.anvil.compiler.internal.reference.Visibility
import com.squareup.anvil.compiler.internal.reference.allSuperTypeClassReferences
import com.squareup.anvil.compiler.internal.reference.argumentAt
import com.squareup.anvil.compiler.internal.reference.asClassName
import com.squareup.anvil.compiler.internal.reference.classAndInnerClassReferences
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier.OVERRIDE
import com.squareup.kotlinpoet.KModifier.PRIVATE
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.jvm.jvmStatic
import com.squareup.kotlinpoet.ksp.TypeParameterResolver
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.toTypeParameterResolver
import com.squareup.kotlinpoet.ksp.toTypeVariableName
import com.squareup.kotlinpoet.ksp.writeTo
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.internal.InstanceFactory
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtFile
import java.io.File
import javax.inject.Provider

internal object AssistedFactoryCodeGen : AnvilApplicabilityChecker {

  override fun isApplicable(context: AnvilContext) = context.generateFactories

  internal class KspGenerator(
    override val env: SymbolProcessorEnvironment,
  ) : AnvilSymbolProcessor() {
    @AutoService(SymbolProcessorProvider::class)
    class Provider : AnvilSymbolProcessorProvider(AssistedFactoryCodeGen, ::KspGenerator)

    override fun processChecked(resolver: Resolver): List<KSAnnotated> {
      resolver.getSymbolsWithAnnotation(assistedFactoryFqName.asString())
        .filterIsInstance<KSClassDeclaration>()
        .forEach { clazz ->
          generateFactoryClass(clazz)
            .writeTo(env.codeGenerator, aggregating = false, listOf(clazz.containingFile!!))
        }
      return emptyList()
    }

    private fun generateFactoryClass(
      clazz: KSClassDeclaration,
    ): FileSpec {
      val typeParameterResolver = clazz.typeParameters.toTypeParameterResolver()
      val function = clazz.requireSingleAbstractFunction(typeParameterResolver)

      val returnType = try {
        function.returnType
      } catch (e: Exception) {
        // Catch the exception and throw the same error that Dagger would.
        throw KspAnvilException(
          message = "Invalid return type: ${clazz.qualifiedName?.asString()}. An assisted factory's " +
            "abstract method must return a type with an @AssistedInject-annotated constructor.",
          node = function.node,
          cause = e,
        )
      }

      // The return type of the function must have an @AssistedInject constructor.
      val constructor = returnType
        .getConstructors()
        .singleOrNull {
          it.isAnnotationPresent<AssistedInject>()
        }
        ?: throw KspAnvilException(
          message = "Invalid return type: ${returnType.qualifiedName?.asString()}. An assisted factory's abstract " +
            "method must return a type with an @AssistedInject-annotated constructor.",
          node = clazz,
        )

      val functionParameters = function.parameterKeys
      val assistedParameters = constructor.parameters.filter { parameter ->
        parameter.isAnnotationPresent<Assisted>()
      }

      // Check that the parameters of the function match the @Assisted parameters of the constructor.
      if (assistedParameters.size != functionParameters.size) {
        throw KspAnvilException(
          message = "The parameters in the factory method must match the @Assisted parameters in " +
            "${returnType.qualifiedName?.asString()}.",
          node = clazz,
        )
      }

      // Compute for each parameter its key.
      val functionParameterKeys = function.parameterKeys
      val assistedParameterKeys = assistedParameters.map {
        it.toAssistedParameterKey(it.type.resolve().toTypeName(typeParameterResolver))
      }

      // The factory function may not have two or more parameters with the same key.
      val duplicateKeys = functionParameterKeys
        .groupBy { it.key }
        .filter { it.value.size > 1 }
        .values
        .flatten()

      if (duplicateKeys.isNotEmpty()) {
        // Complain about the first duplicate key that occurs, similar to Dagger.
        val key = functionParameterKeys.first { it in duplicateKeys }

        throw KspAnvilException(
          message = buildString {
            append("@AssistedFactory method has duplicate @Assisted types: ")
            if (key.identifier.isNotEmpty()) {
              append("@Assisted(\"${key.identifier}\") ")
            }
            append(key.typeName)
          },
          node = clazz,
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
        throw KspAnvilException(
          message = "The parameters in the factory method must match the @Assisted parameters in " +
            "${returnType.qualifiedName?.asString()}.",
          node = clazz,
        )
      }

      val typeParameters = clazz.typeParameters

      val functionName = function.simpleName
      val baseFactoryIsInterface = clazz.isInterface()
      val functionParameterPairs = function.parameterPairs

      val spec = buildSpec(
        originClassNAme = clazz.toClassName(),
        targetType = returnType.toClassName(),
        functionName = functionName,
        typeParameters = typeParameters.map { it.toTypeVariableName(typeParameterResolver) },
        assistedParameterKeys = assistedParameterKeys,
        baseFactoryIsInterface = baseFactoryIsInterface,
        functionParameterPairs = functionParameterPairs.map { (ref, typeName) ->
          ref.name!!.asString() to typeName
        },
        functionParameterKeys = functionParameterKeys,
      )

      return spec
    }

    private fun KSClassDeclaration.requireSingleAbstractFunction(
      typeParameterResolver: TypeParameterResolver,
    ): AssistedFactoryFunction {
      val implementingType = asType(emptyList())

      // `clazz` must be first in the list because of `distinctBy { ... }`, which keeps the first
      // matched element. If the function's inherited, it can be overridden as well. Prioritizing
      // the version from the file we're parsing ensures the correct variance of the referenced types.
      // TODO can't use getAllFunctions() yet due to https://github.com/google/ksp/issues/1619
      val assistedFunctions = sequenceOf(this)
        .plus(getAllSuperTypes().mapNotNull { it.resolveKSClassDeclaration() })
        .distinctBy { it.qualifiedName?.asString() }
        .flatMap { clazz ->
          clazz.getDeclaredFunctions()
            .filter {
              it.isAbstract &&
                (it.getVisibility() == PUBLIC || it.getVisibility() == PROTECTED)
            }
        }
        .distinctBy { it.simpleName.asString() }
        .map {
          it.asMemberOf(implementingType)
            .toAssistedFactoryFunction(it, typeParameterResolver)
        }
        .toList()

      // Check for exact number of functions.
      return when (assistedFunctions.size) {
        0 -> throw KspAnvilException(
          message = "The @AssistedFactory-annotated type is missing an abstract, non-default " +
            "method whose return type matches the assisted injection type.",
          node = this,
        )

        1 -> assistedFunctions[0]
        else -> {
          val foundFunctions = assistedFunctions
            .sortedBy { it.simpleName }
            .joinToString { func ->
              "${func.qualifiedName}(${func.parameterPairs.map { it.first.name }})"
            }
          throw KspAnvilException(
            message = "The @AssistedFactory-annotated type should contain a single abstract, " +
              "non-default method but found multiple: [$foundFunctions]",
            node = this,
          )
        }
      }
    }

    /**
     * Represents a parsed function in an `@AssistedInject.Factory`-annotated interface.
     */
    private data class AssistedFactoryFunction(
      val simpleName: String,
      val qualifiedName: String,
      val returnType: KSClassDeclaration,
      val node: KSNode,
      val parameterKeys: List<AssistedParameterKey>,
      /**
       * Pair of parameter reference to parameter type.
       */
      val parameterPairs: List<Pair<KSValueParameter, TypeName>>,
    ) {

      companion object {
        fun KSFunction.toAssistedFactoryFunction(
          originalDeclaration: KSFunctionDeclaration,
          typeParameterResolver: TypeParameterResolver,
        ): AssistedFactoryFunction {
          return AssistedFactoryFunction(
            simpleName = originalDeclaration.simpleName.asString(),
            qualifiedName = originalDeclaration.qualifiedName!!.asString(),
            returnType = returnType!!.resolveKSClassDeclaration()!!,
            node = originalDeclaration,
            parameterKeys = originalDeclaration.parameters.mapIndexed { index, param ->
              param.toAssistedParameterKey(
                parameterTypes[index]!!.toTypeName(typeParameterResolver),
              )
            },
            parameterPairs = originalDeclaration.parameters.mapIndexed { index, param ->
              param to parameterTypes[index]!!.toTypeName(typeParameterResolver)
            },
          )
        }
      }
    }
  }

  @AutoService(CodeGenerator::class)
  internal class Embedded : PrivateCodeGenerator() {

    override fun isApplicable(context: AnvilContext) = AssistedFactoryCodeGen.isApplicable(context)

    override fun generateCodePrivate(
      codeGenDir: File,
      module: ModuleDescriptor,
      projectFiles: Collection<KtFile>,
    ): List<GeneratedFileWithSources> = projectFiles
      .classAndInnerClassReferences(module)
      .filter { it.isAnnotatedWith(assistedFactoryFqName) }
      .map { clazz ->
        generateFactoryClass(codeGenDir, clazz)
      }
      .toList()

    private fun generateFactoryClass(
      codeGenDir: File,
      clazz: ClassReference.Psi,
    ): GeneratedFileWithSources {
      val function = clazz.requireSingleAbstractFunction()

      val returnType = try {
        function.function.resolveGenericReturnType(clazz)
      } catch (e: AnvilCompilationException) {
        // Catch the exception and throw the same error that Dagger would.
        throw AnvilCompilationExceptionFunctionReference(
          message = "Invalid return type: ${clazz.fqName}. An assisted factory's " +
            "abstract method must return a type with an @AssistedInject-annotated constructor.",
          functionReference = function.function,
          cause = e,
        )
      }

      // The return type of the function must have an @AssistedInject constructor.
      val constructor = returnType
        .constructors
        .singleOrNull { it.isAnnotatedWith(assistedInjectFqName) }
        ?: throw AnvilCompilationExceptionClassReference(
          message = "Invalid return type: ${returnType.fqName}. An assisted factory's abstract " +
            "method must return a type with an @AssistedInject-annotated constructor.",
          classReference = clazz,
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
          classReference = clazz,
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
          classReference = clazz,
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
          classReference = clazz,
        )
      }

      val typeParameters = clazz.typeParameters

      val functionName = function.function.name
      val baseFactoryIsInterface = clazz.isInterface()
      val functionParameterPairs = function.parameterPairs

      val spec = buildSpec(
        originClassNAme = clazz.asClassName(),
        targetType = returnType.asClassName(),
        functionName = functionName,
        typeParameters = typeParameters.map { it.typeVariableName },
        assistedParameterKeys = assistedParameterKeys,
        baseFactoryIsInterface = baseFactoryIsInterface,
        functionParameterPairs = functionParameterPairs.map { (ref, typeName) ->
          ref.name to typeName
        },
        functionParameterKeys = functionParameterKeys,
      )

      return createGeneratedFile(
        codeGenDir = codeGenDir,
        packageName = spec.packageName,
        fileName = spec.name,
        content = spec.toString(),
        sourceFile = clazz.containingFileAsJavaFile,
      )
    }

    private fun ClassReference.Psi.requireSingleAbstractFunction(): AssistedFactoryFunction {
      // `clazz` must be first in the list because of `distinctBy { ... }`, which keeps the first
      // matched element. If the function's inherited, it can be overridden as well. Prioritizing
      // the version from the file we're parsing ensures the correct variance of the referenced types.
      val assistedFunctions = allSuperTypeClassReferences(includeSelf = true)
        .distinctBy { it.fqName }
        .flatMap { clazz ->
          clazz.declaredMemberFunctions
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
          classReference = this,
        )

        1 -> assistedFunctions[0]
        else -> {
          val foundFunctions = assistedFunctions
            .sortedBy { it.function.name }
            .joinToString { func ->
              "${func.function.fqName}(${func.parameterPairs.map { it.first.name }})"
            }
          throw AnvilCompilationExceptionClassReference(
            message = "The @AssistedFactory-annotated type should contain a single abstract, " +
              "non-default method but found multiple: [$foundFunctions]",
            classReference = this,
          )
        }
      }
    }

    /**
     * Represents a parsed function in an `@AssistedInject.Factory`-annotated interface.
     */
    private data class AssistedFactoryFunction(
      val function: MemberFunctionReference,
      val parameterKeys: List<AssistedParameterKey>,
      /**
       * Pair of parameter reference to parameter type.
       */
      val parameterPairs: List<Pair<ParameterReference, TypeName>>,
    ) {
      companion object {
        fun MemberFunctionReference.toAssistedFactoryFunction(
          factoryClass: ClassReference.Psi,
        ): AssistedFactoryFunction {
          return AssistedFactoryFunction(
            function = this,
            parameterKeys = parameters.map { it.toAssistedParameterKey(factoryClass) },
            parameterPairs = parameters.map { it to it.resolveTypeName(factoryClass) },
          )
        }
      }
    }
  }

  private const val DELEGATE_FACTORY_NAME = "delegateFactory"

  private fun buildSpec(
    originClassNAme: ClassName,
    targetType: ClassName,
    functionName: String,
    typeParameters: List<TypeVariableName>,
    assistedParameterKeys: List<AssistedParameterKey>,
    baseFactoryIsInterface: Boolean,
    functionParameterPairs: List<Pair<String, TypeName>>,
    functionParameterKeys: List<AssistedParameterKey>,
  ): FileSpec {
    val generatedFactoryTypeName = targetType.joinSimpleNames(suffix = "_Factory")
      .optionallyParameterizedByNames(typeParameters)

    val baseFactoryTypeName = originClassNAme.optionallyParameterizedByNames(typeParameters)
    val returnTypeName = targetType.optionallyParameterizedByNames(typeParameters)
    val implClassName = originClassNAme.joinSimpleNames(suffix = "_Impl")
    val implParameterizedTypeName = implClassName.optionallyParameterizedByNames(typeParameters)

    return FileSpec.createAnvilSpec(implClassName.packageName, implClassName.simpleName) {
      TypeSpec.classBuilder(implClassName)
        .apply {
          addTypeVariables(typeParameters)

          if (baseFactoryIsInterface) {
            addSuperinterface(baseFactoryTypeName)
          } else {
            superclass(baseFactoryTypeName)
          }

          primaryConstructor(
            FunSpec.constructorBuilder()
              .addParameter(DELEGATE_FACTORY_NAME, generatedFactoryTypeName)
              .build(),
          )

          addProperty(
            PropertySpec.builder(DELEGATE_FACTORY_NAME, generatedFactoryTypeName)
              .initializer(DELEGATE_FACTORY_NAME)
              .addModifiers(PRIVATE)
              .build(),
          )
        }
        .addFunction(
          FunSpec.builder(functionName)
            .addModifiers(OVERRIDE)
            .returns(returnTypeName)
            .apply {
              functionParameterPairs.forEach { parameter ->
                addParameter(parameter.first, parameter.second)
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

                functionParameterPairs[functionIndex].first
              }

              addStatement("return $DELEGATE_FACTORY_NAME.get($argumentList)")
            }
            .build(),
        )
        .apply {
          fun createFactory(name: String, providerTypeName: ClassName): FunSpec {
            return FunSpec.builder(name)
              .jvmStatic()
              .addTypeVariables(typeParameters)
              .addParameter(DELEGATE_FACTORY_NAME, generatedFactoryTypeName)
              .returns(providerTypeName.parameterizedBy(baseFactoryTypeName))
              .addStatement(
                "return %T.create(%T($DELEGATE_FACTORY_NAME))",
                InstanceFactory::class,
                implParameterizedTypeName,
              )
              .build()
          }
          TypeSpec.companionObjectBuilder()
            .addFunction(createFactory("create", Provider::class.asClassName()))
            // New in Dagger 2.50: factories for dagger.internal.Provider
            .addFunction(
              createFactory("createFactoryProvider", dagger.internal.Provider::class.asClassName()),
            )
            .build()
            .let {
              addType(it)
            }
        }
        .build()
        .let { addType(it) }
    }
  }

  // Dagger matches parameters of the factory function with the parameters of the @AssistedInject
  // constructor through a key. Initially, they used the order of parameters, but that has changed.
  // The key is a combination of the type and identifier (value parameter) of the
  // @Assisted("...") annotation. For each parameter the key must be unique.
  private data class AssistedParameterKey(
    val typeName: TypeName,
    val identifier: String,
  ) {

    // Key value is similar to a hash function.  There used to be a special case for KotlinTypes
    // which were parameterized, but this is now handled by KotlinPoet's TypeName.
    // `MyType<String>` and `MyType<Int>` now generate different hashCodes.
    val key: Int = identifier.hashCode() * 31 + typeName.hashCode()

    companion object {
      @OptIn(KspExperimental::class)
      fun KSValueParameter.toAssistedParameterKey(
        typeName: TypeName,
      ): AssistedParameterKey {
        return AssistedParameterKey(
          typeName,
          getAnnotationsByType(Assisted::class)
            .singleOrNull()
            ?.value
            .orEmpty(),
        )
      }

      fun ParameterReference.toAssistedParameterKey(
        factoryClass: ClassReference.Psi,
      ): AssistedParameterKey {
        return AssistedParameterKey(
          typeName = resolveTypeName(factoryClass),
          identifier = annotations
            .singleOrNull { it.fqName == assistedFqName }
            ?.let { annotation ->
              annotation.argumentAt("value", index = 0)?.value<String>()
            }
            .orEmpty(),
        )
      }
    }
  }
}
