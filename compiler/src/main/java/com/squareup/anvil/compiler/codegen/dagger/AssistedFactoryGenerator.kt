package com.squareup.anvil.compiler.codegen.dagger

import com.squareup.anvil.compiler.AnvilCompilationException
import com.squareup.anvil.compiler.assistedFactoryFqName
import com.squareup.anvil.compiler.assistedFqName
import com.squareup.anvil.compiler.assistedInjectFqName
import com.squareup.anvil.compiler.classDescriptorForType
import com.squareup.anvil.compiler.codegen.CodeGenerator.GeneratedFile
import com.squareup.anvil.compiler.codegen.PrivateCodeGenerator
import com.squareup.anvil.compiler.codegen.asClassName
import com.squareup.anvil.compiler.codegen.asTypeName
import com.squareup.anvil.compiler.codegen.buildFile
import com.squareup.anvil.compiler.codegen.classesAndInnerClasses
import com.squareup.anvil.compiler.codegen.createGeneratedFile
import com.squareup.anvil.compiler.codegen.dagger.AssistedFactoryGenerator.AssistedParameterKey.Companion.toKeysList
import com.squareup.anvil.compiler.codegen.hasAnnotation
import com.squareup.anvil.compiler.codegen.requireClassDescriptor
import com.squareup.anvil.compiler.codegen.typeVariableNames
import com.squareup.anvil.compiler.generateClassName
import com.squareup.anvil.compiler.safePackageString
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
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.annotations.argumentValue
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter.Companion.FUNCTIONS
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.isTypeParameter
import java.io.File
import javax.inject.Provider

internal class AssistedFactoryGenerator : PrivateCodeGenerator() {

  override fun generateCodePrivate(
    codeGenDir: File,
    module: ModuleDescriptor,
    projectFiles: Collection<KtFile>
  ) {
    projectFiles
      .asSequence()
      .flatMap { it.classesAndInnerClasses() }
      .filter { it.hasAnnotation(assistedFactoryFqName) }
      .forEach { clazz ->
        generateFactoryClass(codeGenDir, module, clazz)
      }
  }

  private fun generateFactoryClass(
    codeGenDir: File,
    module: ModuleDescriptor,
    clazz: KtClassOrObject
  ): GeneratedFile {
    // It's necessary to resolve the class in order to check super types.
    val classDescriptor = clazz.requireClassDescriptor(module)
    val functions = classDescriptor.unsubstitutedMemberScope
      .getContributedDescriptors(FUNCTIONS)
      .asSequence()
      .filterIsInstance<FunctionDescriptor>()
      .filter { it.modality == ABSTRACT }
      .filter { it.visibility == PUBLIC || it.visibility == PROTECTED }
      .toList()

    // Check for exact number of functions.
    val function = when (functions.size) {
      0 -> throw AnvilCompilationException(
        "The @AssistedFactory-annotated type is missing an abstract, non-default method " +
          "whose return type matches the assisted injection type.",
        element = clazz
      )
      1 -> functions[0]
      else -> throw AnvilCompilationException(
        "The @AssistedFactory-annotated type should contain a single abstract, non-default " +
          "method but found multiple.",
        element = clazz
      )
    }

    val returnType = function.returnType?.classDescriptorForType()
      ?: throw AnvilCompilationException(
        "Couldn't get return type for function.",
        element = function.findPsi()
      )

    // The return type of the function must have an @AssistedInject constructor.
    val constructor = returnType.constructors
      .singleOrNull { it.annotations.findAnnotation(assistedInjectFqName) != null }
      ?: throw AnvilCompilationException(
        "Invalid return type: ${returnType.fqNameSafe}. An assisted factory's abstract " +
          "method must return a type with an @AssistedInject-annotated constructor.",
        element = clazz
      )

    val functionParameters = function.valueParameters
    val assistedParameters = constructor.valueParameters.filter {
      it.annotations.findAnnotation(assistedFqName) != null
    }

    // Check that the parameters of the function match the @Assisted parameters of the constructor.
    if (assistedParameters.size != functionParameters.size) {
      throw AnvilCompilationException(
        "The parameters in the factory method must match the @Assisted parameters in " +
          "${returnType.fqNameSafe}.",
        element = clazz
      )
    }

    // Compute for each parameter its key.
    val functionParameterKeys = functionParameters.toKeysList()
    val assistedParameterKeys = assistedParameters.toKeysList()

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
          append(key.kotlinType.classDescriptorForType().fqNameSafe)
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
          "${returnType.fqNameSafe}.",
        element = clazz
      )
    }

    val packageName = clazz.containingKtFile.packageFqName.safePackageString()
    val className = "${clazz.generateClassName()}_Impl"
    val implClass = ClassName(packageName, className)

    val typeParameters = clazz.typeVariableNames(module)

    fun ClassName.parameterized(): TypeName {
      return if (typeParameters.isEmpty()) this else parameterizedBy(typeParameters)
    }

    val generatedFactory = FqName(returnType.fqNameSafe.asString() + "_Factory")
      .asClassName(module).parameterized()

    val classType = clazz.asClassName().parameterized()
    val delegateFactoryName = "delegateFactory"

    val content = FileSpec.buildFile(packageName, className) {
      TypeSpec.classBuilder(implClass)
        .apply {
          typeParameters.forEach { addTypeVariable(it) }

          if (DescriptorUtils.isInterface(classDescriptor)) {
            addSuperinterface(classType)
          } else {
            superclass(classType)
          }

          primaryConstructor(
            FunSpec.constructorBuilder()
              .addParameter(delegateFactoryName, generatedFactory)
              .build()
          )

          addProperty(
            PropertySpec.builder(delegateFactoryName, generatedFactory)
              .initializer(delegateFactoryName)
              .addModifiers(PRIVATE)
              .build()
          )
        }
        .addFunction(
          FunSpec.builder("create")
            .addModifiers(OVERRIDE)
            .returns(returnType.asClassName().parameterized())
            .apply {
              functionParameters.forEach { parameter ->
                addParameter(parameter.name.asString(), parameter.type.asTypeName())
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

                functionParameters[functionIndex].name.asString()
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
                .addParameter(delegateFactoryName, generatedFactory)
                .returns(Provider::class.asClassName().parameterizedBy(classType))
                .addStatement(
                  "return %T.create(%T($delegateFactoryName))",
                  InstanceFactory::class,
                  implClass.parameterized()
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

    return createGeneratedFile(codeGenDir, packageName, className, content)
  }

  // Dagger matches parameters of the factory function with the parameters of the @AssistedInject
  // constructor through a key. Initially, they used the order of parameters, but that has changed.
  // The key is a combination of the type and identifier (value parameter) of the
  // @Assisted("...") annotation. For each parameter the key must be unique.
  private data class AssistedParameterKey(
    val kotlinType: KotlinType,
    val identifier: String
  ) {

    // That's how we compute the key value. It's similar to a hash function, but with the only
    // condition that the kotlinType cannot be a type parameter. For type parameters
    // (class Abc<TypeParameter>) we use the string representation as key, which must be unique.
    val key: Int = identifier.hashCode() * 31 +
      if (kotlinType.isTypeParameter()) kotlinType.toString().hashCode() else kotlinType.hashCode()

    companion object {
      fun Collection<ValueParameterDescriptor>.toKeysList(): List<AssistedParameterKey> =
        map { descriptor ->
          AssistedParameterKey(
            kotlinType = descriptor.type,
            identifier = descriptor.annotations.findAnnotation(assistedFqName)
              ?.argumentValue("value")
              ?.value
              ?.toString()
              ?: ""
          )
        }
    }
  }
}
