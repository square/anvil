package com.squareup.anvil.compiler.codegen.dagger

import com.google.auto.service.AutoService
import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.api.AnvilContext
import com.squareup.anvil.compiler.api.CodeGenerator
import com.squareup.anvil.compiler.api.GeneratedFile
import com.squareup.anvil.compiler.api.createGeneratedFile
import com.squareup.anvil.compiler.codegen.PrivateCodeGenerator
import com.squareup.anvil.compiler.daggerModuleFqName
import com.squareup.anvil.compiler.daggerProvidesFqName
import com.squareup.anvil.compiler.internal.buildFile
import com.squareup.anvil.compiler.internal.capitalize
import com.squareup.anvil.compiler.internal.reference.AnnotatedReference
import com.squareup.anvil.compiler.internal.reference.AnvilCompilationExceptionFunctionReference
import com.squareup.anvil.compiler.internal.reference.ClassReference
import com.squareup.anvil.compiler.internal.reference.MemberFunctionReference
import com.squareup.anvil.compiler.internal.reference.MemberPropertyReference
import com.squareup.anvil.compiler.internal.reference.TypeReference
import com.squareup.anvil.compiler.internal.reference.Visibility
import com.squareup.anvil.compiler.internal.reference.Visibility.INTERNAL
import com.squareup.anvil.compiler.internal.reference.asClassName
import com.squareup.anvil.compiler.internal.reference.classAndInnerClassReferences
import com.squareup.anvil.compiler.internal.reference.generateClassName
import com.squareup.anvil.compiler.internal.safePackageString
import com.squareup.anvil.compiler.internal.withJvmSuppressWildcardsIfNeeded
import com.squareup.anvil.compiler.isWordPrefixRegex
import com.squareup.anvil.compiler.publishedApiFqName
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
import dagger.internal.Factory
import dagger.internal.Preconditions
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

@AutoService(CodeGenerator::class)
internal class ProvidesMethodFactoryGenerator : PrivateCodeGenerator() {

  override fun isApplicable(context: AnvilContext) = context.generateFactories

  override fun generateCodePrivate(
    codeGenDir: File,
    module: ModuleDescriptor,
    projectFiles: Collection<KtFile>,
  ) {
    projectFiles
      .classAndInnerClassReferences(module)
      .filter { it.isAnnotatedWith(daggerModuleFqName) }
      .forEach { clazz ->
        (clazz.companionObjects() + clazz)
          .asSequence()
          .flatMap { it.functions }
          .filter { it.isAnnotatedWith(daggerProvidesFqName) }
          .onEach { function ->
            checkFunctionIsNotAbstract(clazz, function)
          }
          .also { functions ->
            assertNoDuplicateFunctions(clazz, functions)
          }
          .forEach { function ->
            generateFactoryClass(
              codeGenDir,
              module,
              clazz,
              CallableReference.from(function),
            )
          }

        (clazz.companionObjects() + clazz)
          .asSequence()
          .flatMap { it.properties }
          .filter { property ->
            // Must be '@get:Provides'.
            property.annotations.singleOrNull {
              it.fqName == daggerProvidesFqName
            }?.annotation?.useSiteTarget?.text == "get"
          }
          .forEach { property ->
            generateFactoryClass(
              codeGenDir,
              module,
              clazz,
              CallableReference.from(property),
            )
          }
      }
  }

  private fun generateFactoryClass(
    codeGenDir: File,
    module: ModuleDescriptor,
    clazz: ClassReference.Psi,
    declaration: CallableReference,
  ): GeneratedFile {
    val isCompanionObject = declaration.isCompanionObject
    val isObject = isCompanionObject || clazz.isObject()

    val isProperty = declaration.isProperty
    val declarationName = declaration.name
    // omit the `get-` prefix for property names starting with the *word* `is`, like `isProperty`,
    // but not for names which just start with those letters, like `issues`.
    val useGetPrefix = isProperty && !isWordPrefixRegex.matches(declarationName)

    val isMangled = !isProperty &&
      declaration.isInternal &&
      !declaration.isPublishedApi

    val packageName = clazz.packageFqName.safePackageString()
    val className = buildString {
      append(clazz.generateClassName().relativeClassName.asString())
      append('_')
      if (isCompanionObject) {
        append("Companion_")
      }
      if (useGetPrefix) {
        append("Get")
      }
      append(declarationName.capitalize())
      if (isMangled) {
        append("\$${module.mangledNameSuffix()}")
      }
      append("Factory")
    }

    val callableName = declaration.name

    val parameters = declaration.constructorParameters

    val returnType = declaration.type
    val returnTypeIsNullable = declaration.isNullable

    val factoryClass = ClassName(packageName, className)
    val moduleClass = clazz.asClassName()

    val byteCodeFunctionName = when {
      useGetPrefix -> "get" + callableName.capitalize()
      isMangled -> "$callableName\$${module.mangledNameSuffix()}"
      else -> callableName
    }

    val content = FileSpec.buildFile(packageName, className) {
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
                .build(),
            )

            if (!isObject) {
              addProperty(
                PropertySpec.builder("module", moduleClass)
                  .initializer("module")
                  .addModifiers(PRIVATE)
                  .build(),
              )
            }

            parameters.forEach { parameter ->
              addProperty(
                PropertySpec.builder(parameter.name, parameter.providerTypeName)
                  .initializer(parameter.name)
                  .addModifiers(PRIVATE)
                  .build(),
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
                includeModule = !isObject,
              )
              addStatement("return %N($argumentList)", byteCodeFunctionName)
            }
            .build(),
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
                      includeModule = !isObject,
                    )

                    addStatement("return %T($argumentList)", factoryClass)
                  }
                }
                .returns(factoryClass)
                .build(),
            )
            .addFunction(
              FunSpec.builder(byteCodeFunctionName)
                .jvmStatic()
                .apply {
                  if (!isObject) {
                    addParameter("module", moduleClass)
                  }

                  parameters.forEach { parameter ->
                    addParameter(
                      name = parameter.name,
                      type = parameter.originalTypeName,
                    )
                  }

                  val argumentsWithoutModule = if (isProperty) {
                    ""
                  } else {
                    "(${parameters.joinToString { it.name }})"
                  }

                  when {
                    isObject && returnTypeIsNullable ->
                      addStatement(
                        "return %T.$callableName$argumentsWithoutModule",
                        moduleClass,
                      )
                    isObject && !returnTypeIsNullable ->
                      addStatement(
                        "return %T.checkNotNull(%T.$callableName" +
                          "$argumentsWithoutModule, %S)",
                        Preconditions::class,
                        moduleClass,
                        "Cannot return null from a non-@Nullable @Provides method",
                      )
                    !isObject && returnTypeIsNullable ->
                      addStatement(
                        "return module.$callableName$argumentsWithoutModule",
                      )
                    !isObject && !returnTypeIsNullable ->
                      addStatement(
                        "return %T.checkNotNull(module.$callableName" +
                          "$argumentsWithoutModule, %S)",
                        Preconditions::class,
                        "Cannot return null from a non-@Nullable @Provides method",
                      )
                  }
                }
                .returns(returnType)
                .build(),
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

    return createGeneratedFile(codeGenDir, packageName, className, content)
  }

  private fun checkFunctionIsNotAbstract(
    clazz: ClassReference.Psi,
    function: MemberFunctionReference.Psi,
  ) {
    fun fail(): Nothing = throw AnvilCompilationExceptionFunctionReference(
      message = "@Provides methods cannot be abstract",
      functionReference = function,
    )

    // If the function is abstract, then it's an error.
    if (function.isAbstract()) fail()

    // If the class is not an interface and doesn't use the abstract keyword, then there is
    // no issue.
    if (!clazz.isInterface()) return

    // If the parent of the function is a companion object, then the function inside of the
    // interface is not abstract.
    if (function.declaringClass.isCompanion()) return

    fail()
  }

  private fun ModuleDescriptor.mangledNameSuffix(): String {
    // We replace - with _ to maintain interoperability with Dagger's expected generated identifiers
    val name = name.asString().replace('-', '_')
    return if (name.startsWith('<') && name.endsWith('>')) {
      name.substring(1, name.length - 1)
    } else {
      name
    }
  }

  private class CallableReference(
    val isInternal: Boolean,
    val isCompanionObject: Boolean,
    val name: String,
    val isProperty: Boolean,
    val constructorParameters: List<ConstructorParameter>,
    val type: TypeName,
    val isNullable: Boolean,
    val isPublishedApi: Boolean,
  ) {

    companion object {
      fun from(function: MemberFunctionReference.Psi): CallableReference {
        val type = function.returnTypeOrNull() ?: throw AnvilCompilationExceptionFunctionReference(
          message = "Dagger provider methods must specify the return type explicitly when using " +
            "Anvil. The return type cannot be inferred implicitly.",
          functionReference = function,
        )
        val typeName = type.asTypeName().withJvmSuppressWildcardsIfNeeded(function, type)
        return CallableReference(
          isInternal = function.visibility() == INTERNAL,
          isCompanionObject = function.declaringClass.isCompanion(),
          name = function.name,
          isProperty = false,
          constructorParameters = function.parameters.mapToConstructorParameters(),
          type = typeName,
          isNullable = type.isNullable(),
          isPublishedApi = function.isAnnotatedWith(publishedApiFqName),
        )
      }
      fun from(property: MemberPropertyReference.Psi): CallableReference {
        val type = property.type()
        val typeName = type.asTypeName().withJvmSuppressWildcardsIfNeeded(property, type)
        return CallableReference(
          isInternal = property.visibility() == INTERNAL,
          isCompanionObject = property.declaringClass.isCompanion(),
          name = property.name,
          isProperty = true,
          constructorParameters = emptyList(),
          type = typeName,
          isNullable = type.isNullable(),
          isPublishedApi = property.isAnnotatedWith(publishedApiFqName),
        )
      }
    }
  }
}
