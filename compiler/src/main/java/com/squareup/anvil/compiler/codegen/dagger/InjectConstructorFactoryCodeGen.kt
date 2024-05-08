package com.squareup.anvil.compiler.codegen.dagger

import com.google.auto.service.AutoService
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.anvil.compiler.api.AnvilApplicabilityChecker
import com.squareup.anvil.compiler.api.AnvilContext
import com.squareup.anvil.compiler.api.CodeGenerator
import com.squareup.anvil.compiler.api.GeneratedFileWithSources
import com.squareup.anvil.compiler.api.createGeneratedFile
import com.squareup.anvil.compiler.codegen.PrivateCodeGenerator
import com.squareup.anvil.compiler.codegen.injectConstructor
import com.squareup.anvil.compiler.codegen.ksp.AnvilSymbolProcessor
import com.squareup.anvil.compiler.codegen.ksp.AnvilSymbolProcessorProvider
import com.squareup.anvil.compiler.codegen.ksp.injectConstructors
import com.squareup.anvil.compiler.codegen.ksp.isAnnotationPresent
import com.squareup.anvil.compiler.injectFqName
import com.squareup.anvil.compiler.internal.containingFileAsJavaFile
import com.squareup.anvil.compiler.internal.createAnvilSpec
import com.squareup.anvil.compiler.internal.joinSimpleNames
import com.squareup.anvil.compiler.internal.reference.ClassReference
import com.squareup.anvil.compiler.internal.reference.MemberFunctionReference
import com.squareup.anvil.compiler.internal.reference.asClassName
import com.squareup.anvil.compiler.internal.reference.classAndInnerClassReferences
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier.OVERRIDE
import com.squareup.kotlinpoet.KModifier.PRIVATE
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.jvm.jvmStatic
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeParameterResolver
import com.squareup.kotlinpoet.ksp.toTypeVariableName
import com.squareup.kotlinpoet.ksp.writeTo
import dagger.internal.Factory
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtFile
import java.io.File
import javax.inject.Inject

internal object InjectConstructorFactoryCodeGen : AnvilApplicabilityChecker {
  override fun isApplicable(context: AnvilContext) = context.generateFactories

  internal class KspGenerator(
    override val env: SymbolProcessorEnvironment,
  ) : AnvilSymbolProcessor() {
    @AutoService(SymbolProcessorProvider::class)
    class Provider : AnvilSymbolProcessorProvider(InjectConstructorFactoryCodeGen, ::KspGenerator)

    override fun processChecked(resolver: Resolver): List<KSAnnotated> {
      resolver.injectConstructors()
        .forEach { (_, constructor) ->
          if (!constructor.isAnnotationPresent<Inject>()) {
            // Only generating @Inject constructors
            return@forEach
          }

          generateFactoryClass(constructor)
            .writeTo(
              env.codeGenerator,
              aggregating = false,
              originatingKSFiles = listOf(constructor.containingFile!!),
            )
        }

      return emptyList()
    }

    private fun generateFactoryClass(
      constructor: KSFunctionDeclaration,
    ): FileSpec {
      val clazz = constructor.parentDeclaration as KSClassDeclaration
      val constructorParameters = constructor.parameters.mapToConstructorParameters(
        clazz.typeParameters.toTypeParameterResolver(),
      )
      val memberInjectParameters = clazz.memberInjectParameters()
      val typeParameters = clazz.typeParameters.map { it.toTypeVariableName() }

      return generateFactoryClass(
        injectedClassName = clazz.toClassName(),
        typeParameters = typeParameters,
        constructorParameters = constructorParameters,
        memberInjectParameters = memberInjectParameters,
      )
    }
  }

  @AutoService(CodeGenerator::class)
  internal class Embedded : PrivateCodeGenerator() {

    override fun isApplicable(context: AnvilContext) = InjectConstructorFactoryCodeGen.isApplicable(
      context,
    )

    override fun generateCodePrivate(
      codeGenDir: File,
      module: ModuleDescriptor,
      projectFiles: Collection<KtFile>,
    ): List<GeneratedFileWithSources> = projectFiles
      .classAndInnerClassReferences(module)
      .mapNotNull { clazz ->
        clazz.constructors
          .injectConstructor()
          ?.takeIf { it.isAnnotatedWith(injectFqName) }
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
      val typeParameters = clazz.typeParameters.map { it.typeVariableName }

      val spec = generateFactoryClass(
        injectedClassName = clazz.asClassName(),
        typeParameters = typeParameters,
        constructorParameters = constructorParameters,
        memberInjectParameters = memberInjectParameters,
      )

      return createGeneratedFile(
        codeGenDir = codeGenDir,
        packageName = spec.packageName,
        fileName = spec.name,
        content = spec.toString(),
        sourceFile = clazz.clazz.containingFileAsJavaFile(),
      )
    }
  }

  private fun generateFactoryClass(
    injectedClassName: ClassName,
    typeParameters: List<TypeVariableName>,
    constructorParameters: List<ConstructorParameter>,
    memberInjectParameters: List<MemberInjectParameter>,
  ): FileSpec {
    val generatedClassName = injectedClassName.joinSimpleNames(suffix = "_Factory")

    val packageName = injectedClassName.packageName

    val allParameters = constructorParameters + memberInjectParameters

    val factoryClassParameterized = generatedClassName.optionallyParameterizedByNames(
      typeParameters,
    )
    val classType = injectedClassName.optionallyParameterizedByNames(typeParameters)

    val spec = FileSpec.createAnvilSpec(packageName, generatedClassName.simpleName) {
      val canGenerateAnObject = allParameters.isEmpty() && typeParameters.isEmpty()
      val classBuilder = if (canGenerateAnObject) {
        TypeSpec.objectBuilder(generatedClassName)
      } else {
        TypeSpec.classBuilder(generatedClassName)
      }
      typeParameters.forEach { classBuilder.addTypeVariable(it) }

      classBuilder
        .addSuperinterface(Factory::class.asClassName().parameterizedBy(classType))
        .apply {
          if (allParameters.isNotEmpty()) {
            primaryConstructor(
              FunSpec.constructorBuilder()
                .apply {
                  allParameters.forEach { parameter ->
                    addParameter(parameter.name, parameter.providerTypeName)
                  }
                }
                .build(),
            )

            allParameters.forEach { parameter ->
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
            .returns(classType)
            .apply {
              val newInstanceArgumentList = constructorParameters.asArgumentList(
                asProvider = true,
                includeModule = false,
              )

              if (memberInjectParameters.isEmpty()) {
                addStatement("return newInstance($newInstanceArgumentList)")
              } else {
                val instanceName = "instance"
                addStatement("val $instanceName = newInstance($newInstanceArgumentList)")
                addMemberInjection(memberInjectParameters, instanceName)
                addStatement("return $instanceName")
              }
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
                  if (typeParameters.isNotEmpty()) {
                    addTypeVariables(typeParameters)
                  }
                  if (canGenerateAnObject) {
                    addStatement("return this")
                  } else {
                    allParameters.forEach { parameter ->
                      addParameter(parameter.name, parameter.providerTypeName)
                    }

                    val argumentList = allParameters.asArgumentList(
                      asProvider = false,
                      includeModule = false,
                    )

                    addStatement(
                      "return %T($argumentList)",
                      factoryClassParameterized,
                    )
                  }
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
              if (!canGenerateAnObject) {
                addType(it)
              }
            }
        }
        .build()
        .let { addType(it) }
    }

    return spec
  }
}
