package com.squareup.anvil.compiler.codegen

import com.google.auto.service.AutoService
import com.squareup.anvil.annotations.ContributesSubcomponent
import com.squareup.anvil.compiler.HINT_SUBCOMPONENTS_PACKAGE_PREFIX
import com.squareup.anvil.compiler.REFERENCE_SUFFIX
import com.squareup.anvil.compiler.SCOPE_SUFFIX
import com.squareup.anvil.compiler.api.AnvilContext
import com.squareup.anvil.compiler.api.CodeGenerator
import com.squareup.anvil.compiler.api.GeneratedFile
import com.squareup.anvil.compiler.api.createGeneratedFile
import com.squareup.anvil.compiler.contributesSubcomponentFactoryFqName
import com.squareup.anvil.compiler.contributesSubcomponentFqName
import com.squareup.anvil.compiler.contributesToFqName
import com.squareup.anvil.compiler.daggerSubcomponentBuilderFqName
import com.squareup.anvil.compiler.daggerSubcomponentFactoryFqName
import com.squareup.anvil.compiler.internal.buildFile
import com.squareup.anvil.compiler.internal.reference.AnvilCompilationExceptionClassReference
import com.squareup.anvil.compiler.internal.reference.ClassReference
import com.squareup.anvil.compiler.internal.reference.Visibility
import com.squareup.anvil.compiler.internal.reference.asClassName
import com.squareup.anvil.compiler.internal.reference.classAndInnerClassReferences
import com.squareup.anvil.compiler.internal.reference.generateClassName
import com.squareup.anvil.compiler.internal.reference.isAbstract
import com.squareup.anvil.compiler.internal.reference.isAnnotatedWith
import com.squareup.anvil.compiler.internal.reference.returnType
import com.squareup.anvil.compiler.internal.safePackageString
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier.PUBLIC
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.asClassName
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtFile
import java.io.File
import kotlin.reflect.KClass

/**
 * Generates a hint for each contributed subcomponent in the `anvil.hint.subcomponent` packages.
 * This allows the compiler plugin to find all contributed classes a lot faster.
 */
@AutoService(CodeGenerator::class)
internal class ContributesSubcomponentGenerator : CodeGenerator {

  override fun isApplicable(context: AnvilContext) = !context.generateFactoriesOnly

  override fun generateCode(
    codeGenDir: File,
    module: ModuleDescriptor,
    projectFiles: Collection<KtFile>
  ): Collection<GeneratedFile> {
    return projectFiles
      .classAndInnerClassReferences(module)
      .filter { it.isAnnotatedWith(contributesSubcomponentFqName) }
      .onEach { clazz ->
        if (!clazz.isInterface() && !clazz.isAbstract()) {
          throw AnvilCompilationExceptionClassReference(
            message = "${clazz.fqName} is annotated with " +
              "@${ContributesSubcomponent::class.simpleName}, but this class is not an interface.",
            classReference = clazz
          )
        }

        if (clazz.visibility() != Visibility.PUBLIC) {
          throw AnvilCompilationExceptionClassReference(
            message = "${clazz.fqName} is contributed to the Dagger graph, but the " +
              "interface is not public. Only public interfaces are supported.",
            classReference = clazz
          )
        }

        clazz.annotations
          .filter { it.fqName == contributesSubcomponentFqName }
          .forEach { annotation ->
            annotation.replaces().forEach {
              it.checkUsesSameScope(annotation.scope(), clazz)
            }
          }
      }
      .map { clazz ->
        val fileName = clazz.generateClassName()
        val generatedPackage = HINT_SUBCOMPONENTS_PACKAGE_PREFIX +
          clazz.packageFqName.safePackageString(dotPrefix = true)
        val className = clazz.asClassName()
        val classFqName = clazz.fqName.toString()
        val propertyName = classFqName.replace('.', '_')
        val parentScopeReference = clazz.annotations
          .single { it.fqName == contributesSubcomponentFqName }
          .parentScope()
        val parentScope = parentScopeReference.asClassName()

        clazz.checkParentComponentInterface(clazz.innerClasses(), parentScopeReference)
        clazz.checkFactory(clazz.innerClasses())

        val content =
          FileSpec.buildFile(generatedPackage, fileName) {
            addProperty(
              PropertySpec
                .builder(
                  name = propertyName + REFERENCE_SUFFIX,
                  type = KClass::class.asClassName().parameterizedBy(className)
                )
                .initializer("%T::class", className)
                .addModifiers(PUBLIC)
                .build()
            )

            addProperty(
              PropertySpec
                .builder(
                  name = propertyName + SCOPE_SUFFIX,
                  type = KClass::class.asClassName().parameterizedBy(parentScope)
                )
                .initializer("%T::class", parentScope)
                .addModifiers(PUBLIC)
                .build()
            )
          }

        createGeneratedFile(
          codeGenDir = codeGenDir,
          packageName = generatedPackage,
          fileName = fileName,
          content = content
        )
      }
      .toList()
  }

  private fun ClassReference.checkParentComponentInterface(
    innerClasses: List<ClassReference>,
    parentScope: ClassReference
  ) {
    val parentComponents = innerClasses
      .filter {
        it.annotations.any { annotation ->
          annotation.fqName == contributesToFqName && annotation.scope() == parentScope
        }
      }

    val componentInterface = when (parentComponents.size) {
      0 -> return
      1 -> parentComponents[0]
      else -> throw AnvilCompilationExceptionClassReference(
        classReference = this,
        message = "Expected zero or one parent component interface within " +
          "$fqName being contributed to the parent scope."
      )
    }

    val functions = componentInterface.functions
      .filter { it.returnType() == this }

    if (functions.size >= 2) {
      throw AnvilCompilationExceptionClassReference(
        classReference = componentInterface,
        message = "Expected zero or one function returning the subcomponent $fqName."
      )
    }
  }

  private fun ClassReference.checkFactory(innerClasses: List<ClassReference>) {
    innerClasses
      .firstOrNull { it.isAnnotatedWith(daggerSubcomponentFactoryFqName) }
      ?.let { factoryClass ->
        throw AnvilCompilationExceptionClassReference(
          classReference = factoryClass,
          message = "Within a class using @${ContributesSubcomponent::class.simpleName} you " +
            "must use $contributesSubcomponentFactoryFqName and not " +
            "$daggerSubcomponentFactoryFqName."
        )
      }

    innerClasses
      .firstOrNull { it.isAnnotatedWith(daggerSubcomponentBuilderFqName) }
      ?.let { factoryClass ->
        throw AnvilCompilationExceptionClassReference(
          classReference = factoryClass,
          message = "Within a class using @${ContributesSubcomponent::class.simpleName} you " +
            "must use $contributesSubcomponentFactoryFqName and not " +
            "$daggerSubcomponentBuilderFqName. Builders aren't supported."
        )
      }

    val factories = innerClasses
      .filter { it.isAnnotatedWith(contributesSubcomponentFactoryFqName) }

    val factory = when (factories.size) {
      0 -> return
      1 -> factories[0]
      else -> throw AnvilCompilationExceptionClassReference(
        classReference = this,
        message = "Expected zero or one factory within $fqName."
      )
    }

    if (!factory.isInterface() && !factory.isAbstract()) {
      throw AnvilCompilationExceptionClassReference(
        classReference = factory,
        message = "A factory must be an interface or an abstract class."
      )
    }

    val functions = factory.functions
      .let { functions ->
        if (factory.isInterface()) {
          functions
        } else {
          functions.filter { it.isAbstract() }
        }
      }

    if (functions.size != 1 || functions[0].returnType() != this) {
      throw AnvilCompilationExceptionClassReference(
        classReference = factory,
        message = "A factory must have exactly one abstract function returning the " +
          "subcomponent $fqName."
      )
    }
  }

  private fun ClassReference.checkUsesSameScope(
    scope: ClassReference,
    subcomponent: ClassReference
  ) {
    annotations
      .filter { it.fqName == contributesSubcomponentFqName }
      .ifEmpty {
        throw AnvilCompilationExceptionClassReference(
          classReference = subcomponent,
          message = "Couldn't find the annotation @ContributesSubcomponent for $fqName."
        )
      }
      .forEach { annotation ->
        val otherScope = annotation.scope()
        if (otherScope != scope) {
          throw AnvilCompilationExceptionClassReference(
            classReference = subcomponent,
            message = "${subcomponent.fqName} with scope ${scope.fqName} wants to replace " +
              "$fqName with scope ${otherScope.fqName}. The replacement must use the same scope."
          )
        }
      }
  }
}
