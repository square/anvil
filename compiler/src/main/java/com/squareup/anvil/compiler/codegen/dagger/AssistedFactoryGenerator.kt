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
import com.squareup.anvil.compiler.codegen.dagger.AssistedFactoryGenerator.AssistedParameterKey.Companion.toKeysList
import com.squareup.anvil.compiler.codegen.injectConstructor
import com.squareup.anvil.compiler.internal.allPsiSuperTypes
import com.squareup.anvil.compiler.internal.asClassName
import com.squareup.anvil.compiler.internal.asTypeName
import com.squareup.anvil.compiler.internal.buildFile
import com.squareup.anvil.compiler.internal.classDescriptorForType
import com.squareup.anvil.compiler.internal.classDescriptorOrNull
import com.squareup.anvil.compiler.internal.classesAndInnerClass
import com.squareup.anvil.compiler.internal.findAnnotation
import com.squareup.anvil.compiler.internal.findAnnotationArgument
import com.squareup.anvil.compiler.internal.fqNameOrNull
import com.squareup.anvil.compiler.internal.functions
import com.squareup.anvil.compiler.internal.generateClassName
import com.squareup.anvil.compiler.internal.getKtClassOrObjectOrNull
import com.squareup.anvil.compiler.internal.hasAnnotation
import com.squareup.anvil.compiler.internal.isInterface
import com.squareup.anvil.compiler.internal.requireFqName
import com.squareup.anvil.compiler.internal.requireTypeName
import com.squareup.anvil.compiler.internal.safePackageString
import com.squareup.anvil.compiler.internal.typeVariableNames
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
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities.PROTECTED
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities.PUBLIC
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.Modality.ABSTRACT
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType
import org.jetbrains.kotlin.psi.psiUtil.isProtected
import org.jetbrains.kotlin.psi.psiUtil.isPublic
import org.jetbrains.kotlin.resolve.annotations.argumentValue
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import java.io.File
import javax.inject.Provider
import kotlin.LazyThreadSafetyMode.NONE

@AutoService(CodeGenerator::class)
internal class AssistedFactoryGenerator : PrivateCodeGenerator() {

  override fun isApplicable(context: AnvilContext) = context.generateFactories

  override fun generateCodePrivate(
    codeGenDir: File,
    module: ModuleDescriptor,
    projectFiles: Collection<KtFile>
  ) {
    projectFiles
      .classesAndInnerClass(module)
      .filter { it.hasAnnotation(assistedFactoryFqName, module) }
      .forEach { clazz ->
        generateFactoryClass(codeGenDir, module, clazz)
      }
  }

  private fun generateFactoryClass(
    codeGenDir: File,
    module: ModuleDescriptor,
    clazz: KtClassOrObject
  ): GeneratedFile {

    val packageName = clazz.containingKtFile.packageFqName.safePackageString()

    val delegateFactoryName = "delegateFactory"

    val function = clazz.requireSingleAbstractFunction(module)

    val returnTypeFqName = function.returnTypeLazy.value

    // The return type of the function must have an @AssistedInject constructor.
    val constructor = module.getKtClassOrObjectOrNull(returnTypeFqName)
      ?.injectConstructor(assistedInjectFqName, module)
      ?: throw AnvilCompilationException(
        "Invalid return type: $returnTypeFqName. An assisted factory's abstract " +
          "method must return a type with an @AssistedInject-annotated constructor.",
        element = clazz
      )

    val functionParameters = function.parameterKeys.value
    val assistedParameters = constructor.valueParameters.filter {
      it.findAnnotation(assistedFqName, module) != null
    }

    // Check that the parameters of the function match the @Assisted parameters of the constructor.
    if (assistedParameters.size != functionParameters.size) {
      throw AnvilCompilationException(
        "The parameters in the factory method must match the @Assisted parameters in " +
          "$returnTypeFqName.",
        element = clazz
      )
    }

    // Compute for each parameter its key.
    val functionParameterKeys = function.parameterKeys.value
    val assistedParameterKeys = assistedParameters.toKeysList(module)

    // The factory function may not have two or more parameters with the same key.
    val duplicateKeys = functionParameterKeys
      .groupBy { it.key }
      .filter { it.value.size > 1 }
      .values
      .flatten()

    if (duplicateKeys.isNotEmpty()) {
      // Complain about the first duplicate key that occurs, similar to Dagger.
      val key = functionParameterKeys.first { it in duplicateKeys }

      throw AnvilCompilationException(
        message = buildString {
          append("@AssistedFactory method has duplicate @Assisted types: ")
          if (key.identifier.isNotEmpty()) {
            append("@Assisted(\"${key.identifier}\") ")
          }
          append(key.typeName)
        },
        element = clazz
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
      throw AnvilCompilationException(
        "The parameters in the factory method must match the @Assisted parameters in " +
          "$returnTypeFqName.",
        element = clazz
      )
    }

    val typeParameters = clazz.typeVariableNames(module)

    fun ClassName.parameterized(): TypeName {
      return if (typeParameters.isEmpty()) this else parameterizedBy(typeParameters)
    }

    val generatedFactoryTypeName = FqName(returnTypeFqName.generateClassName(suffix = "_Factory"))
      .asClassName(module)
      .parameterized()

    val baseFactoryTypeName = clazz.asClassName().parameterized()

    val returnTypeName = returnTypeFqName.asClassName(module).parameterized()

    val implClassName = ClassName(packageName, "${clazz.generateClassName()}_Impl")
    val implParameterizedTypeName = implClassName.parameterized()
    val functionName = function.name
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
              functionParameterPairs.value
                .forEach { parameter ->
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

                functionParameterPairs.value[functionIndex].first
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

  /**
   * Represents a parsed function in an `@AssistedInject.Factory`-annotated interface.
   */
  private data class AssistedFactoryFunction(
    val name: String,
    val returnTypeLazy: Lazy<FqName>,
    val parameterKeys: Lazy<List<AssistedParameterKey>>,
    /**
     * Pair of parameter name to parameter type
     */
    val parameterPairs: Lazy<List<Pair<String, TypeName>>>
  ) {
    companion object {
      fun FunctionDescriptor.toAssistedFactoryFunction(
        factoryClass: KtClassOrObject
      ): AssistedFactoryFunction {
        val returnTypeLazy = lazy(NONE) {
          returnType
            ?.classDescriptorForType()
            ?.fqNameSafe
            ?: throw AnvilCompilationException(
              "Invalid return type: ${factoryClass.requireFqName()}. An assisted factory's " +
                "abstract method must return a type with an @AssistedInject-annotated constructor.",
              element = factoryClass
            )
        }
        val parameterPairs = lazy(NONE) {
          valueParameters.map { param ->
            param.name.asString() to param.type.asTypeName()
          }
        }
        return AssistedFactoryFunction(
          name = name.asString(),
          returnTypeLazy = returnTypeLazy,
          parameterKeys = lazy(NONE) { valueParameters.toKeysList() },
          parameterPairs = parameterPairs
        )
      }

      fun KtNamedFunction.toAssistedFactoryFunction(
        module: ModuleDescriptor
      ): AssistedFactoryFunction {
        val returnTypeLazy = lazy(NONE) {
          typeReference
            ?.fqNameOrNull(module)
            ?: throw AnvilCompilationException(
              "Invalid return type: ${requireFqName()}. An assisted factory's abstract " +
                "method must return a type with an @AssistedInject-annotated constructor.",
              element = this
            )
        }
        val parameterPairs = lazy(NONE) {
          valueParameters.map { param ->

            val typeName = param.typeReference
              ?.requireTypeName(module)
              ?: throw AnvilCompilationException(
                "Couldn't get type for parameter.",
                element = param
              )

            param.nameAsSafeName.asString() to typeName
          }
        }
        return AssistedFactoryFunction(
          name = name!!,
          returnTypeLazy = returnTypeLazy,
          parameterKeys = lazy(NONE) { valueParameters.toKeysList(module) },
          parameterPairs = parameterPairs
        )
      }
    }
  }

  private fun KtClassOrObject.requireSingleAbstractFunction(
    module: ModuleDescriptor
  ): AssistedFactoryFunction {

    val psiSuperTypes = allPsiSuperTypes(module).toList()

    // Go as far as we can with Psi, then at the end, get the FqName of the last Psi element.
    // In case there's a deep hierarchy, we need to try to resolve super types using descriptors
    val superFqName = psiSuperTypes.lastOrNull()
      ?.fqName
      ?: requireFqName()

    // Try to resolve super-types using descriptors, but don't use "require" in case the code is
    // generated.  It's important to try resolving the most "super" FqName that we know about,
    // because the annotated class could be generated.
    val functionsFromDescriptor = superFqName.classDescriptorOrNull(module)
      ?.unsubstitutedMemberScope
      ?.getContributedDescriptors(DescriptorKindFilter.FUNCTIONS)
      ?.asSequence()
      ?.filterIsInstance<FunctionDescriptor>()
      ?.filter { it.modality == ABSTRACT }
      ?.filter { it.visibility == PUBLIC || it.visibility == PROTECTED }
      ?.map { it.toAssistedFactoryFunction(this) }
      ?.toList()
      .orEmpty()

    // `clazz` must be first in the list because of `distinctBy { ... }`, which keeps the first
    // matched element.  If the function's inherited, it can be overridden as well.  Prioritizing
    // the version from the file we're parsing ensures the correct variance of the referenced types.
    val functions = listOf(this)
      .plus(psiSuperTypes)
      .flatMap { type ->

        type.takeIf { it.isInterface() }
          ?.functions(false)
          ?: type.functions(false)
            .filter { it.hasModifier(KtTokens.ABSTRACT_KEYWORD) }
            .filter { it.isPublic || it.isProtected() }
      }
      .map { it.toAssistedFactoryFunction(module) }
      .plus(functionsFromDescriptor)
      .distinctBy { it.name }

    // Check for exact number of functions.
    return when (functions.size) {
      0 -> throw AnvilCompilationException(
        "The @AssistedFactory-annotated type is missing an abstract, non-default method " +
          "whose return type matches the assisted injection type.",
        element = this
      )
      1 -> functions[0]
      else -> throw AnvilCompilationException(
        "The @AssistedFactory-annotated type should contain a single abstract, non-default " +
          "method but found multiple.",
        element = this
      )
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
      fun Collection<ValueParameterDescriptor>.toKeysList(): List<AssistedParameterKey> =
        map { descriptor ->

          val type = descriptor.type.asTypeName()

          AssistedParameterKey(
            typeName = type,
            identifier = descriptor.annotations.findAnnotation(assistedFqName)
              ?.argumentValue("value")
              ?.value
              ?.toString()
              ?: ""
          )
        }

      @JvmName("ughGenerics")
      fun Collection<KtParameter>.toKeysList(module: ModuleDescriptor): List<AssistedParameterKey> =
        map { parameter ->

          val typeName = parameter.typeReference?.requireTypeName(module)
            ?: throw AnvilCompilationException(
              "Couldn't get type for parameter.",
              element = parameter
            )

          val identifier = parameter.findAnnotation(assistedFqName, module)
            ?.findAnnotationArgument<KtStringTemplateExpression>("value", 0)
            ?.getChildOfType<KtStringTemplateEntry>()
            ?.text
            ?: ""

          AssistedParameterKey(
            typeName = typeName,
            identifier = identifier
          )
        }
    }
  }
}
