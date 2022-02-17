package com.squareup.anvil.compiler.codegen.dagger

import com.google.auto.service.AutoService
import com.squareup.anvil.compiler.api.AnvilContext
import com.squareup.anvil.compiler.api.CodeGenerator
import com.squareup.anvil.compiler.api.GeneratedFile
import com.squareup.anvil.compiler.api.createGeneratedFile
import com.squareup.anvil.compiler.codegen.PrivateCodeGenerator
import com.squareup.anvil.compiler.internal.buildFile
import com.squareup.anvil.compiler.internal.capitalize
import com.squareup.anvil.compiler.internal.isGenericClass
import com.squareup.anvil.compiler.internal.reference.ClassReference
import com.squareup.anvil.compiler.internal.reference.asClassName
import com.squareup.anvil.compiler.internal.reference.classAndInnerClassReferences
import com.squareup.anvil.compiler.internal.reference.generateClassName
import com.squareup.anvil.compiler.internal.safePackageString
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier.OVERRIDE
import com.squareup.kotlinpoet.KModifier.PRIVATE
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.jvm.jvmStatic
import dagger.MembersInjector
import dagger.internal.InjectedFieldSignature
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

@AutoService(CodeGenerator::class)
internal class MembersInjectorGenerator : PrivateCodeGenerator() {

  override fun isApplicable(context: AnvilContext) = context.generateFactories

  override fun generateCodePrivate(
    codeGenDir: File,
    module: ModuleDescriptor,
    projectFiles: Collection<KtFile>
  ) {
    projectFiles
      .classAndInnerClassReferences(module)
      .filterNot { it.isInterface() }
      .forEach { clazz ->

        // Only generate a MembersInjector if the target class declares its own member-injected
        // properties. If it does, then any properties from superclasses must be added as well.
        val declaredInjectedProperties = clazz.clazz.injectedMembers(module)
          .ifEmpty { return@forEach }

        val parameters = clazz
          .memberInjectParameters(inheritedOnly = true)
          .let { inherited ->
            inherited + declaredInjectedProperties.mapToMemberInjectParameters(module, inherited)
          }

        generateMembersInjectorClass(
          codeGenDir = codeGenDir,
          clazz = clazz,
          parameters = parameters
        )
      }
  }

  private fun generateMembersInjectorClass(
    codeGenDir: File,
    clazz: ClassReference.Psi,
    parameters: List<MemberInjectParameter>
  ): GeneratedFile {
    val packageName = clazz.packageFqName.safePackageString()
    val className = "${clazz.generateClassName()}_MembersInjector"
    val classType = clazz.asClassName()
      .let {
        if (clazz.clazz.isGenericClass()) {
          it.parameterizedBy(List(size = clazz.clazz.typeParameters.size) { STAR })
        } else {
          it
        }
      }

    fun createArgumentList(asProvider: Boolean): String {
      return parameters
        .map { it.name }
        .let { list ->
          if (asProvider) list.map { "$it.get()" } else list
        }
        .joinToString()
    }

    val memberInjectorClass = ClassName(packageName, className)

    val content = FileSpec.buildFile(packageName, className) {
      addType(
        TypeSpec
          .classBuilder(memberInjectorClass)
          .addSuperinterface(MembersInjector::class.asClassName().parameterizedBy(classType))
          .apply {
            primaryConstructor(
              FunSpec.constructorBuilder()
                .apply {
                  parameters.forEach { parameter ->
                    addParameter(parameter.name, parameter.providerTypeName)
                  }
                }
                .build()
            )

            parameters.forEach { parameter ->
              addProperty(
                PropertySpec.builder(parameter.name, parameter.providerTypeName)
                  .initializer(parameter.name)
                  .addModifiers(PRIVATE)
                  .build()
              )
            }
          }
          .addFunction(
            FunSpec.builder("injectMembers")
              .addModifiers(OVERRIDE)
              .addParameter("instance", classType)
              .addMemberInjection(parameters, "instance")
              .build()
          )
          .addType(
            TypeSpec
              .companionObjectBuilder()
              .addFunction(
                FunSpec.builder("create")
                  .jvmStatic()
                  .apply {
                    parameters.forEach { parameter ->
                      addParameter(parameter.name, parameter.providerTypeName)
                    }

                    addStatement(
                      "return %T(${createArgumentList(false)})",
                      memberInjectorClass
                    )
                  }
                  .returns(memberInjectorClass)
                  .build()
              )
              .apply {
                parameters
                  // Don't generate the static single-property "inject___" functions for super-classes
                  .filter { it.memberInjectorClassName == memberInjectorClass }
                  .forEach { parameter ->

                    val name = parameter.name

                    addFunction(
                      FunSpec.builder("inject${parameter.accessName.capitalize()}")
                        .jvmStatic()
                        .apply {
                          // Don't add @InjectedFieldSignature when it's calling a setter method
                          if (!parameter.isSetterInjected) {
                            addAnnotation(
                              AnnotationSpec.builder(InjectedFieldSignature::class)
                                .addMember("%S", parameter.injectedFieldSignature)
                                .build()
                            )
                          }
                        }
                        .addAnnotations(parameter.qualifierAnnotationSpecs)
                        .addParameter("instance", classType)
                        .addParameter(name, parameter.originalTypeName)
                        .addStatement("instance.${parameter.originalName} = $name")
                        .build()
                    )
                  }
              }
              .build()
          )
          .build()
      )
    }

    return createGeneratedFile(codeGenDir, packageName, className, content)
  }
}
