package com.squareup.anvil.compiler.codegen.dagger

import com.google.auto.service.AutoService
import com.squareup.anvil.compiler.api.AnvilContext
import com.squareup.anvil.compiler.api.CodeGenerator
import com.squareup.anvil.compiler.api.GeneratedFile
import com.squareup.anvil.compiler.api.createGeneratedFile
import com.squareup.anvil.compiler.codegen.PrivateCodeGenerator
import com.squareup.anvil.compiler.codegen.mapToParameter
import com.squareup.anvil.compiler.daggerDoubleCheckFqNameString
import com.squareup.anvil.compiler.internal.asClassName
import com.squareup.anvil.compiler.internal.buildFile
import com.squareup.anvil.compiler.internal.capitalize
import com.squareup.anvil.compiler.internal.classesAndInnerClass
import com.squareup.anvil.compiler.internal.generateClassName
import com.squareup.anvil.compiler.internal.isGenericClass
import com.squareup.anvil.compiler.internal.isQualifier
import com.squareup.anvil.compiler.internal.requireClassDescriptor
import com.squareup.anvil.compiler.internal.requireFqName
import com.squareup.anvil.compiler.internal.safePackageString
import com.squareup.anvil.compiler.internal.toAnnotationSpec
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
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
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
      .classesAndInnerClass(module)
      .forEach { clazz ->
        val injectProperties = clazz.injectedMembers(module)
          .ifEmpty { return@forEach }

        generateMembersInjectorClass(
          codeGenDir = codeGenDir,
          module = module,
          clazz = clazz,
          injectProperties = injectProperties
        )
      }
  }

  private fun generateMembersInjectorClass(
    codeGenDir: File,
    module: ModuleDescriptor,
    clazz: KtClassOrObject,
    injectProperties: List<KtProperty>
  ): GeneratedFile {
    val packageName = clazz.containingKtFile.packageFqName.safePackageString()
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

    // Lazily evaluated, only computed if any properties have any non-inject annotations.
    val propertyDescriptors by lazy {
      clazz.requireClassDescriptor(module)
        .unsubstitutedMemberScope
        .getContributedDescriptors(DescriptorKindFilter.VARIABLES)
        .filterIsInstance<PropertyDescriptor>()
        .associateBy { it.name.asString() }
    }

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
              .apply {
                parameters.forEachIndexed { index, parameter ->
                  val property = injectProperties[index]
                  val propertyName = property.nameAsSafeName.asString()

                  val parameterString = when {
                    parameter.isWrappedInProvider -> parameter.name
                    parameter.isWrappedInLazy ->
                      "$daggerDoubleCheckFqNameString.lazy(${parameter.name})"
                    else -> parameter.name + ".get()"
                  }
                  addStatement("inject${propertyName.capitalize()}(instance, $parameterString)")
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
                    FunSpec.builder("inject${propertyName.capitalize()}")
                      .jvmStatic()
                      .addAnnotation(
                        AnnotationSpec.builder(InjectedFieldSignature::class)
                          .addMember("%S", property.requireFqName())
                          .build()
                      )
                      .apply {
                        val hasQualifier = property.annotationEntries
                          .any { it.isQualifier(module) }

                        if (hasQualifier) {
                          addAnnotations(
                            propertyDescriptors.getValue(propertyName)
                              .annotations
                              .filter { it.isQualifier() }
                              .map { it.toAnnotationSpec(module) }
                          )
                        }
                      }
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
    }

    return createGeneratedFile(codeGenDir, packageName, className, content)
  }
}
