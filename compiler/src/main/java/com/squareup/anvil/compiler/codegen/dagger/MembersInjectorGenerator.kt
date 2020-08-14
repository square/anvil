package com.squareup.anvil.compiler.codegen.dagger

import com.squareup.anvil.compiler.codegen.CodeGenerator.GeneratedFile
import com.squareup.anvil.compiler.codegen.PrivateCodeGenerator
import com.squareup.anvil.compiler.codegen.addGeneratedByComment
import com.squareup.anvil.compiler.codegen.asClassName
import com.squareup.anvil.compiler.codegen.classesAndInnerClasses
import com.squareup.anvil.compiler.codegen.hasAnnotation
import com.squareup.anvil.compiler.codegen.isGenericClass
import com.squareup.anvil.compiler.codegen.mapToParameter
import com.squareup.anvil.compiler.codegen.requireFqName
import com.squareup.anvil.compiler.codegen.writeToString
import com.squareup.anvil.compiler.daggerDoubleCheckFqNameString
import com.squareup.anvil.compiler.generateClassName
import com.squareup.anvil.compiler.injectFqName
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
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifierTypeOrDefault
import java.io.File
import java.util.Locale.US

internal class MembersInjectorGenerator : PrivateCodeGenerator() {

  override fun generateCodePrivate(
    codeGenDir: File,
    module: ModuleDescriptor,
    projectFiles: Collection<KtFile>
  ) {
    projectFiles
        .asSequence()
        .flatMap { it.classesAndInnerClasses() }
        .forEach { clazz ->
          val injectProperties = clazz.children
              .asSequence()
              .filterIsInstance<KtClassBody>()
              .flatMap { it.properties.asSequence() }
              .filterNot { it.visibilityModifierTypeOrDefault() == KtTokens.PRIVATE_KEYWORD }
              .filter { it.hasAnnotation(injectFqName) }
              .toList()
              .ifEmpty { return@forEach }

          generateMembersInjectorClass(codeGenDir, module, clazz, injectProperties)
        }
  }

  private fun generateMembersInjectorClass(
    codeGenDir: File,
    module: ModuleDescriptor,
    clazz: KtClassOrObject,
    injectProperties: List<KtProperty>
  ): GeneratedFile {
    val packageName = clazz.containingKtFile.packageFqName.asString()
    val className = "${clazz.generateClassName()}_MembersInjector"
    val classType = clazz.asClassName()
        .let {
          if (clazz.isGenericClass()) {
            it.parameterizedBy(List(size = clazz.typeParameters.size) { STAR })
          } else {
            it
          }
        }

    val parameters = injectProperties.mapToParameter(module)

    fun createArgumentList(asProvider: Boolean): String {
      return parameters
          .map { it.name }
          .let { list ->
            if (asProvider) list.map { "$it.get()" } else list
          }
          .joinToString()
    }

    val memberInjectorClass = ClassName(packageName, className)

    val content = FileSpec.builder(packageName, className)
        .addType(
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
                        .apply {
                          parameters.forEachIndexed { index, parameter ->
                            val property = injectProperties[index]
                            val propertyName = property.nameAsSafeName.asString()

                            addStatement(
                                "inject${propertyName.capitalize(US)}(instance, ${
                                  when {
                                    parameter.isWrappedInProvider -> parameter.name
                                    parameter.isWrappedInLazy ->
                                      "$daggerDoubleCheckFqNameString.lazy(${parameter.name})"
                                    else -> parameter.name + ".get()"
                                  }
                                })"
                            )
                          }
                        }
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
                          parameters.forEachIndexed { index, parameter ->
                            val property = injectProperties[index]
                            val propertyName = property.nameAsSafeName.asString()

                            addFunction(
                                FunSpec.builder("inject${propertyName.capitalize(US)}")
                                    .jvmStatic()
                                    .addAnnotation(
                                        AnnotationSpec.builder(InjectedFieldSignature::class)
                                            .addMember("%S", property.requireFqName())
                                            .build()
                                    )
                                    .addParameter("instance", classType)
                                    .addParameter(propertyName, parameter.originalTypeName)
                                    .addStatement("instance.$propertyName = $propertyName")
                                    .build()
                            )
                          }
                        }
                        .build()
                )
                .build()
        )
        .build()
        .writeToString()
        .addGeneratedByComment()

    val directory = File(codeGenDir, packageName.replace('.', File.separatorChar))
    val file = File(directory, "$className.kt")
    check(file.parentFile.exists() || file.parentFile.mkdirs()) {
      "Could not generate package directory: ${file.parentFile}"
    }
    file.writeText(content)

    return GeneratedFile(file, content)
  }
}
