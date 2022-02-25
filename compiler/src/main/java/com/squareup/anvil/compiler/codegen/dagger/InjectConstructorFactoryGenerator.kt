package com.squareup.anvil.compiler.codegen.dagger

import com.google.auto.service.AutoService
import com.squareup.anvil.compiler.api.AnvilContext
import com.squareup.anvil.compiler.api.CodeGenerator
import com.squareup.anvil.compiler.api.GeneratedFile
import com.squareup.anvil.compiler.api.createGeneratedFile
import com.squareup.anvil.compiler.codegen.PrivateCodeGenerator
import com.squareup.anvil.compiler.codegen.injectConstructor
import com.squareup.anvil.compiler.injectFqName
import com.squareup.anvil.compiler.internal.asClassName
import com.squareup.anvil.compiler.internal.buildFile
import com.squareup.anvil.compiler.internal.optionallyParameterizedBy
import com.squareup.anvil.compiler.internal.reference.ClassReference
import com.squareup.anvil.compiler.internal.reference.FunctionReference
import com.squareup.anvil.compiler.internal.reference.asClassName
import com.squareup.anvil.compiler.internal.reference.classAndInnerClassReferences
import com.squareup.anvil.compiler.internal.reference.generateClassName
import com.squareup.anvil.compiler.internal.safePackageString
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
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

@AutoService(CodeGenerator::class)
internal class InjectConstructorFactoryGenerator : PrivateCodeGenerator() {

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
          ?.takeIf { it.isAnnotatedWith(injectFqName) }
          ?.let {
            generateFactoryClass(codeGenDir, clazz, it)
          }
      }
  }

  private fun generateFactoryClass(
    codeGenDir: File,
    clazz: ClassReference.Psi,
    constructor: FunctionReference.Psi
  ): GeneratedFile {
    val classId = clazz.generateClassName(suffix = "_Factory")

    val packageName = classId.packageFqName.safePackageString()
    val className = classId.relativeClassName.asString()

    val constructorParameters = constructor.parameters.mapToConstructorParameters()
    val memberInjectParameters = clazz.memberInjectParameters()

    val allParameters = constructorParameters + memberInjectParameters

    val typeParameters = clazz.typeParameters

    val factoryClass = classId.asClassName()
    val factoryClassParameterized = factoryClass.optionallyParameterizedBy(typeParameters)
    val classType = clazz.asClassName().optionallyParameterizedBy(typeParameters)

    val content = FileSpec.buildFile(packageName, className) {
      val canGenerateAnObject = allParameters.isEmpty() && typeParameters.isEmpty()
      val classBuilder = if (canGenerateAnObject) {
        TypeSpec.objectBuilder(factoryClass)
      } else {
        TypeSpec.classBuilder(factoryClass)
      }
      typeParameters.forEach { classBuilder.addTypeVariable(it.typeVariableName) }

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
                .build()
            )

            allParameters.forEach { parameter ->
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
            .returns(classType)
            .apply {
              val newInstanceArgumentList = constructorParameters.asArgumentList(
                asProvider = true,
                includeModule = false
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
            .build()
        )
        .apply {
          val builder = if (canGenerateAnObject) this else TypeSpec.companionObjectBuilder()
          builder
            .addFunction(
              FunSpec.builder("create")
                .jvmStatic()
                .apply {
                  if (typeParameters.isNotEmpty()) {
                    addTypeVariables(typeParameters.map { it.typeVariableName })
                  }
                  if (canGenerateAnObject) {
                    addStatement("return this")
                  } else {
                    allParameters.forEach { parameter ->
                      addParameter(parameter.name, parameter.providerTypeName)
                    }

                    val argumentList = allParameters.asArgumentList(
                      asProvider = false,
                      includeModule = false
                    )

                    addStatement(
                      "return %T($argumentList)",
                      factoryClassParameterized
                    )
                  }
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
}
