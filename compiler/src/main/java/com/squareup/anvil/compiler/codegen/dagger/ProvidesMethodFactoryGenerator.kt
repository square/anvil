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
import com.squareup.anvil.compiler.internal.reference.AnvilCompilationExceptionClassReference
import com.squareup.anvil.compiler.internal.reference.AnvilCompilationExceptionFunctionReference
import com.squareup.anvil.compiler.internal.reference.ClassReference
import com.squareup.anvil.compiler.internal.reference.FunctionReference
import com.squareup.anvil.compiler.internal.reference.PropertyReference
import com.squareup.anvil.compiler.internal.reference.TypeReference
import com.squareup.anvil.compiler.internal.reference.Visibility.INTERNAL
import com.squareup.anvil.compiler.internal.reference.asClassName
import com.squareup.anvil.compiler.internal.reference.classAndInnerClassReferences
import com.squareup.anvil.compiler.internal.reference.generateClassName
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
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

@AutoService(CodeGenerator::class)
internal class ProvidesMethodFactoryGenerator : PrivateCodeGenerator() {

  override fun isApplicable(context: AnvilContext) = context.generateFactories

  override fun generateCodePrivate(
    codeGenDir: File,
    module: ModuleDescriptor,
    projectFiles: Collection<KtFile>
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
            // Check for duplicate function names.
            val duplicateFunctions = functions
              .groupBy { it.fqName }
              .filterValues { it.size > 1 }

            if (duplicateFunctions.isNotEmpty()) {
              throw AnvilCompilationExceptionClassReference(
                classReference = clazz,
                message = "Cannot have more than one binding method with the same name in " +
                  "a single module: ${duplicateFunctions.keys.joinToString()}"
              )
            }
          }
          .forEach { function ->
            generateFactoryClass(
              codeGenDir,
              module,
              clazz,
              CallableReference(function = function)
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
              CallableReference(property = property)
            )
          }
      }
  }

  private fun generateFactoryClass(
    codeGenDir: File,
    module: ModuleDescriptor,
    clazz: ClassReference.Psi,
    declaration: CallableReference
  ): GeneratedFile {
    val isCompanionObject = declaration.declaringClass.isCompanion()
    val isObject = isCompanionObject || clazz.isObject()

    val isProperty = declaration.isProperty
    val declarationName = declaration.fqName.shortName().asString()
    val useGetPrefix = isProperty && !declarationName.startsWith("is")

    val isMangled = !isProperty &&
      declaration.visibility == INTERNAL &&
      !declaration.isAnnotatedWith(publishedApiFqName)

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

    val returnType = declaration.type.asTypeName()
      .withJvmSuppressWildcardsIfNeeded(declaration.annotationReference, declaration.type)
    val returnTypeIsNullable = declaration.type.isNullable()

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
    clazz: ClassReference.Psi,
    function: FunctionReference.Psi
  ) {
    fun fail(): Nothing = throw AnvilCompilationExceptionFunctionReference(
      message = "@Provides methods cannot be abstract",
      functionReference = function
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
    val name = name.asString()
    return if (name.startsWith('<') && name.endsWith('>')) {
      name.substring(1, name.length - 1)
    } else {
      name
    }
  }

  private class CallableReference(
    private val function: FunctionReference.Psi? = null,
    private val property: PropertyReference.Psi? = null
  ) {

    init {
      if (function == null && property == null) {
        throw AnvilCompilationException(
          "Cannot create a CallableReference wrapper without a " +
            "function OR a property"
        )
      }
    }

    val declaringClass = function?.declaringClass ?: property!!.declaringClass

    val visibility
      get() = function?.visibility() ?: property!!.visibility()

    val fqName = function?.fqName ?: property!!.fqName
    val name = function?.name ?: property!!.name

    val isProperty = property != null

    val constructorParameters: List<ConstructorParameter> =
      function?.parameters?.mapToConstructorParameters() ?: emptyList()

    val type: TypeReference = function?.let {
      it.returnTypeOrNull() ?: throw AnvilCompilationExceptionFunctionReference(
        message = "Dagger provider methods must specify the return type explicitly when using " +
          "Anvil. The return type cannot be inferred implicitly.",
        functionReference = it
      )
    } ?: property!!.type()
    val annotationReference: AnnotatedReference = function ?: property!!

    fun isAnnotatedWith(fqName: FqName): Boolean {
      return function?.isAnnotatedWith(fqName) ?: property!!.isAnnotatedWith(fqName)
    }
  }
}
