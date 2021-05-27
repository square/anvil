package com.squareup.anvil.compiler.codegen.dagger

import com.google.auto.service.AutoService
import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.api.AnvilContext
import com.squareup.anvil.compiler.api.CodeGenerator
import com.squareup.anvil.compiler.api.GeneratedFile
import com.squareup.anvil.compiler.api.createGeneratedFile
import com.squareup.anvil.compiler.codegen.PrivateCodeGenerator
import com.squareup.anvil.compiler.codegen.asArgumentList
import com.squareup.anvil.compiler.codegen.mapToParameter
import com.squareup.anvil.compiler.daggerModuleFqName
import com.squareup.anvil.compiler.daggerProvidesFqName
import com.squareup.anvil.compiler.internal.asClassName
import com.squareup.anvil.compiler.internal.buildFile
import com.squareup.anvil.compiler.internal.classesAndInnerClasses
import com.squareup.anvil.compiler.internal.findAnnotation
import com.squareup.anvil.compiler.internal.functions
import com.squareup.anvil.compiler.internal.generateClassName
import com.squareup.anvil.compiler.internal.hasAnnotation
import com.squareup.anvil.compiler.internal.isInterface
import com.squareup.anvil.compiler.internal.isNullable
import com.squareup.anvil.compiler.internal.properties
import com.squareup.anvil.compiler.internal.requireFqName
import com.squareup.anvil.compiler.internal.requireTypeName
import com.squareup.anvil.compiler.internal.requireTypeReference
import com.squareup.anvil.compiler.internal.safePackageString
import com.squareup.anvil.compiler.internal.withJvmSuppressWildcardsIfNeeded
import com.squareup.anvil.compiler.publishedApiFqName
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
import org.jetbrains.kotlin.lexer.KtTokens.ABSTRACT_KEYWORD
import org.jetbrains.kotlin.lexer.KtTokens.INTERNAL_KEYWORD
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifierTypeOrDefault
import java.io.File
import java.util.Locale.US

@AutoService(CodeGenerator::class)
internal class ProvidesMethodFactoryGenerator : PrivateCodeGenerator() {

  override fun isApplicable(context: AnvilContext) = context.generateFactories

  override fun generateCodePrivate(
    codeGenDir: File,
    module: ModuleDescriptor,
    projectFiles: Collection<KtFile>
  ) {
    projectFiles
      .asSequence()
      .flatMap { it.classesAndInnerClasses() }
      .filter { it.hasAnnotation(daggerModuleFqName, module) }
      .forEach { clazz ->
        clazz
          .functions(includeCompanionObjects = true)
          .asSequence()
          .filter { it.hasAnnotation(daggerProvidesFqName, module) }
          .onEach { function ->
            checkFunctionIsNotAbstract(clazz, function)
          }
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

        clazz
          .properties(includeCompanionObjects = true)
          .asSequence()
          .filter { property ->
            // Must be '@get:Provides'.
            property.findAnnotation(daggerProvidesFqName, module)?.useSiteTarget?.text == "get"
          }
          .forEach { property ->
            generateFactoryClass(codeGenDir, module, clazz, property)
          }
      }
  }

  private fun generateFactoryClass(
    codeGenDir: File,
    module: ModuleDescriptor,
    clazz: KtClassOrObject,
    declaration: KtCallableDeclaration
  ): GeneratedFile {
    val isCompanionObject = declaration.parents
      .filterIsInstance<KtObjectDeclaration>()
      .firstOrNull()
      ?.isCompanion()
      ?: false
    val isObject = isCompanionObject || clazz is KtObjectDeclaration

    val isProperty = declaration is KtProperty

    val isMangled = !isProperty &&
      declaration.visibilityModifierTypeOrDefault() == INTERNAL_KEYWORD &&
      !declaration.hasAnnotation(publishedApiFqName, module)

    val packageName = clazz.containingKtFile.packageFqName.safePackageString()
    val className = buildString {
      append(clazz.generateClassName())
      append('_')
      if (isCompanionObject) {
        append("Companion_")
      }
      if (isProperty) {
        append("Get")
      }
      append(declaration.requireFqName().shortName().asString().capitalize(US))
      if (isMangled) {
        append("\$${module.mangledNameSuffix()}")
      }
      append("Factory")
    }

    val callableName = declaration.nameAsSafeName.asString()

    val parameters = declaration.valueParameters.mapToParameter(module)

    val returnType = declaration.requireTypeReference(module).requireTypeName(module)
      .withJvmSuppressWildcardsIfNeeded(declaration, module)
    val returnTypeIsNullable = declaration.typeReference?.isNullable() ?: false

    val factoryClass = ClassName(packageName, className)
    val moduleClass = clazz.asClassName()

    val byteCodeFunctionName = when {
      isProperty -> "get" + callableName.capitalize(US)
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
              addStatement("return %N($argumentList)", byteCodeFunctionName)
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
              FunSpec.builder(byteCodeFunctionName)
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

                  val argumentsWithoutModule = if (isProperty) {
                    ""
                  } else {
                    "(${parameters.joinToString { it.name }})"
                  }

                  when {
                    isObject && returnTypeIsNullable ->
                      addStatement(
                        "return %T.$callableName$argumentsWithoutModule",
                        moduleClass
                      )
                    isObject && !returnTypeIsNullable ->
                      addStatement(
                        "return %T.checkNotNull(%T.$callableName" +
                          "$argumentsWithoutModule, %S)",
                        Preconditions::class,
                        moduleClass,
                        "Cannot return null from a non-@Nullable @Provides method"
                      )
                    !isObject && returnTypeIsNullable ->
                      addStatement(
                        "return module.$callableName$argumentsWithoutModule"
                      )
                    !isObject && !returnTypeIsNullable ->
                      addStatement(
                        "return %T.checkNotNull(module.$callableName" +
                          "$argumentsWithoutModule, %S)",
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

    return createGeneratedFile(codeGenDir, packageName, className, content)
  }

  private fun checkFunctionIsNotAbstract(
    clazz: KtClassOrObject,
    function: KtFunction
  ) {
    fun fail(): Nothing = throw AnvilCompilationException(
      "@Provides methods cannot be abstract",
      element = function
    )

    // If the function is abstract, then it's an error.
    if (function.hasModifier(ABSTRACT_KEYWORD)) fail()

    // If the class is not an interface and doesn't use the abstract keyword, then there is
    // no issue.
    if (!clazz.isInterface()) return

    // If the parent of the function is a companion object, then the function inside of the
    // interface is not abstract.
    val parentClass = function.parents.filterIsInstance<KtClassOrObject>().firstOrNull() ?: return
    if (parentClass is KtObjectDeclaration && parentClass.isCompanion()) return

    fail()
  }

  private fun ModuleDescriptor.mangledNameSuffix(): String {
    val name = name.asString()
    return if (name.startsWith('<') && name.endsWith('>')) {
      name.substring(1, name.length - 1)
    } else {
      name
    }
  }
}
