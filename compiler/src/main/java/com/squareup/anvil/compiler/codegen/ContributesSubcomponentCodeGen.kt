package com.squareup.anvil.compiler.codegen

import com.google.auto.service.AutoService
import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.anvil.annotations.ContributesSubcomponent
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.anvil.compiler.HINT_PACKAGE
import com.squareup.anvil.compiler.REFERENCE_SUFFIX
import com.squareup.anvil.compiler.SCOPE_SUFFIX
import com.squareup.anvil.compiler.api.AnvilApplicabilityChecker
import com.squareup.anvil.compiler.api.AnvilContext
import com.squareup.anvil.compiler.api.CodeGenerator
import com.squareup.anvil.compiler.api.GeneratedFileWithSources
import com.squareup.anvil.compiler.api.createGeneratedFile
import com.squareup.anvil.compiler.codegen.ksp.AnvilSymbolProcessor
import com.squareup.anvil.compiler.codegen.ksp.AnvilSymbolProcessorProvider
import com.squareup.anvil.compiler.codegen.ksp.KspAnvilException
import com.squareup.anvil.compiler.codegen.ksp.getKSAnnotationsByType
import com.squareup.anvil.compiler.codegen.ksp.isAnnotationPresent
import com.squareup.anvil.compiler.codegen.ksp.isInterface
import com.squareup.anvil.compiler.codegen.ksp.parentScope
import com.squareup.anvil.compiler.codegen.ksp.replaces
import com.squareup.anvil.compiler.codegen.ksp.resolveKSClassDeclaration
import com.squareup.anvil.compiler.codegen.ksp.scope
import com.squareup.anvil.compiler.contributesSubcomponentFactoryFqName
import com.squareup.anvil.compiler.contributesSubcomponentFqName
import com.squareup.anvil.compiler.contributesToFqName
import com.squareup.anvil.compiler.daggerSubcomponentBuilderFqName
import com.squareup.anvil.compiler.daggerSubcomponentFactoryFqName
import com.squareup.anvil.compiler.internal.createAnvilSpec
import com.squareup.anvil.compiler.internal.generateHintFileName
import com.squareup.anvil.compiler.internal.reference.AnvilCompilationExceptionClassReference
import com.squareup.anvil.compiler.internal.reference.ClassReference
import com.squareup.anvil.compiler.internal.reference.Visibility
import com.squareup.anvil.compiler.internal.reference.asClassName
import com.squareup.anvil.compiler.internal.reference.classAndInnerClassReferences
import com.squareup.anvil.compiler.internal.reference.returnTypeWithGenericSubstitution
import com.squareup.anvil.compiler.internal.reference.returnTypeWithGenericSubstitutionOrNull
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier.PUBLIC
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import dagger.Subcomponent
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtFile
import java.io.File
import kotlin.reflect.KClass

/**
 * Generates a hint for each contributed subcomponent in the `anvil.hint` packages.
 * This allows the compiler plugin to find all contributed classes a lot faster.
 */
internal object ContributesSubcomponentCodeGen : AnvilApplicabilityChecker {
  override fun isApplicable(context: AnvilContext) = !context.generateFactoriesOnly

  internal class KspGenerator(
    override val env: SymbolProcessorEnvironment,
  ) : AnvilSymbolProcessor() {
    @AutoService(SymbolProcessorProvider::class)
    class Provider : AnvilSymbolProcessorProvider(ContributesSubcomponentCodeGen, ::KspGenerator)

    override fun processChecked(resolver: Resolver): List<KSAnnotated> {
      resolver.getSymbolsWithAnnotation(contributesSubcomponentFqName.asString())
        .filterIsInstance<KSClassDeclaration>()
        .onEach { clazz ->
          if (!clazz.isInterface() && !clazz.isAbstract()) {
            throw KspAnvilException(
              message = "${clazz.qualifiedName?.asString()} is annotated with " +
                "@${ContributesSubcomponent::class.simpleName}, but this class is not an interface.",
              node = clazz,
            )
          }

          if (clazz.getVisibility() != com.google.devtools.ksp.symbol.Visibility.PUBLIC) {
            throw KspAnvilException(
              message = "${clazz.qualifiedName?.asString()} is contributed to the Dagger graph, but the " +
                "interface is not public. Only public interfaces are supported.",
              node = clazz,
            )
          }

          clazz.getKSAnnotationsByType(ContributesSubcomponent::class)
            .forEach { annotation ->
              for (it in annotation.replaces()) {
                val scope =
                  annotation.scope().resolveKSClassDeclaration() ?: throw KspAnvilException(
                    message = "Couldn't resolve the scope for ${clazz.qualifiedName?.asString()}.",
                    node = clazz,
                  )
                it.checkUsesSameScope(scope, clazz)
              }
            }
        }
        .forEach { clazz ->
          clazz.checkFactory(clazz.declarations.filterIsInstance<KSClassDeclaration>())
          val className = clazz.toClassName()
          val parentScopeDeclaration = clazz.getKSAnnotationsByType(ContributesSubcomponent::class)
            .single()
            .parentScope()
          clazz.checkParentComponentInterface(
            clazz.declarations.filterIsInstance<KSClassDeclaration>(),
            parentScopeDeclaration,
          )
          val parentScope = parentScopeDeclaration.toClassName()

          createSpec(className, parentScope)
            .writeTo(
              env.codeGenerator,
              aggregating = false,
              originatingKSFiles = listOf(clazz.containingFile!!),
            )
        }

      return emptyList()
    }

    private fun KSClassDeclaration.checkParentComponentInterface(
      innerClasses: Sequence<KSClassDeclaration>,
      parentScope: KSClassDeclaration,
    ) {
      val parentComponents = innerClasses
        .filter {
          it.getKSAnnotationsByType(ContributesTo::class)
            .any { annotation ->
              annotation.scope().resolveKSClassDeclaration() == parentScope
            }
        }
        .toList()

      val componentInterface = when (parentComponents.size) {
        0 -> return
        1 -> parentComponents[0]
        else -> throw KspAnvilException(
          node = this,
          message = "Expected zero or one parent component interface within " +
            "${qualifiedName?.asString()} being contributed to the parent scope.",
        )
      }

      val functions = componentInterface.getAllFunctions()
        .filter {
          it.returnType?.resolve()?.resolveKSClassDeclaration() == this
        }
        .toList()

      if (functions.size >= 2) {
        throw KspAnvilException(
          node = componentInterface,
          message = "Expected zero or one function returning the subcomponent ${qualifiedName?.asString()}.",
        )
      }
    }

    private fun KSClassDeclaration.checkFactory(innerClasses: Sequence<KSClassDeclaration>) {
      innerClasses
        .firstOrNull { it.isAnnotationPresent<Subcomponent.Factory>() }
        ?.let { factoryClass ->
          throw KspAnvilException(
            node = factoryClass,
            message = "Within a class using @${ContributesSubcomponent::class.simpleName} you " +
              "must use $contributesSubcomponentFactoryFqName and not " +
              "$daggerSubcomponentFactoryFqName.",
          )
        }

      innerClasses
        .firstOrNull { it.isAnnotationPresent<Subcomponent.Builder>() }
        ?.let { factoryClass ->
          throw KspAnvilException(
            node = factoryClass,
            message = "Within a class using @${ContributesSubcomponent::class.simpleName} you " +
              "must use $contributesSubcomponentFactoryFqName and not " +
              "$daggerSubcomponentBuilderFqName. Builders aren't supported.",
          )
        }

      val factories = innerClasses
        .filter { it.isAnnotationPresent<ContributesSubcomponent.Factory>() }
        .toList()

      val factory = when (factories.size) {
        0 -> return
        1 -> factories[0]
        else -> throw KspAnvilException(
          node = this,
          message = "Expected zero or one factory within ${qualifiedName?.asString()}.",
        )
      }

      if (!factory.isInterface() && !factory.isAbstract()) {
        throw KspAnvilException(
          node = factory,
          message = "A factory must be an interface or an abstract class.",
        )
      }

      val functions = factory.getAllFunctions()
        .filter { it.isAbstract }
        .toList()

      if (functions.size != 1 || functions[0].returnType?.resolve()
          ?.resolveKSClassDeclaration() != this
      ) {
        throw KspAnvilException(
          node = factory,
          message = "A factory must have exactly one abstract function returning the " +
            "subcomponent ${qualifiedName?.asString()}.",
        )
      }
    }

    private fun KSClassDeclaration.checkUsesSameScope(
      scope: KSClassDeclaration,
      subcomponent: KSClassDeclaration,
    ) {
      getKSAnnotationsByType(ContributesSubcomponent::class)
        .ifEmpty {
          throw KspAnvilException(
            node = subcomponent,
            message = "Couldn't find the annotation @ContributesSubcomponent for ${qualifiedName?.asString()}.",
          )
        }
        .forEach { annotation ->
          val otherScope = annotation.scope().resolveKSClassDeclaration() ?: return@forEach
          if (otherScope != scope) {
            throw KspAnvilException(
              node = subcomponent,
              message = "${subcomponent.qualifiedName?.asString()} with scope ${scope.qualifiedName?.asString()} wants to replace " +
                "${qualifiedName?.asString()} with scope ${otherScope.qualifiedName?.asString()}. The replacement must use the same scope.",
            )
          }
        }
    }
  }

  @AutoService(CodeGenerator::class)
  internal class Embedded : CodeGenerator {

    override fun isApplicable(context: AnvilContext) = ContributesSubcomponentCodeGen.isApplicable(
      context,
    )

    override fun generateCode(
      codeGenDir: File,
      module: ModuleDescriptor,
      projectFiles: Collection<KtFile>,
    ): Collection<GeneratedFileWithSources> {
      return projectFiles
        .classAndInnerClassReferences(module)
        .filter { it.isAnnotatedWith(contributesSubcomponentFqName) }
        .onEach { clazz ->
          if (!clazz.isInterface() && !clazz.isAbstract()) {
            throw AnvilCompilationExceptionClassReference(
              message = "${clazz.fqName} is annotated with " +
                "@${ContributesSubcomponent::class.simpleName}, but this class is not an interface.",
              classReference = clazz,
            )
          }

          if (clazz.visibility() != Visibility.PUBLIC) {
            throw AnvilCompilationExceptionClassReference(
              message = "${clazz.fqName} is contributed to the Dagger graph, but the " +
                "interface is not public. Only public interfaces are supported.",
              classReference = clazz,
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
          clazz.checkFactory(clazz.innerClasses())
          val className = clazz.asClassName()
          val parentScopeReference = clazz.annotations
            .single { it.fqName == contributesSubcomponentFqName }
            .parentScope()
          clazz.checkParentComponentInterface(clazz.innerClasses(), parentScopeReference)
          val parentScope = parentScopeReference.asClassName()

          val spec = createSpec(className, parentScope)

          createGeneratedFile(
            codeGenDir = codeGenDir,
            packageName = spec.packageName,
            fileName = spec.name,
            content = spec.toString(),
            sourceFile = clazz.containingFileAsJavaFile,
          )
        }
        .toList()
    }

    private fun ClassReference.checkParentComponentInterface(
      innerClasses: List<ClassReference>,
      parentScope: ClassReference,
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
            "$fqName being contributed to the parent scope.",
        )
      }

      val functions = componentInterface.memberFunctions
        .filter { function ->

          val returnType = function
            .returnTypeWithGenericSubstitution(componentInterface)
            .asClassReference()
          returnType == this
        }

      if (functions.size >= 2) {
        throw AnvilCompilationExceptionClassReference(
          classReference = componentInterface,
          message = "Expected zero or one function returning the subcomponent $fqName.",
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
              "$daggerSubcomponentFactoryFqName.",
          )
        }

      innerClasses
        .firstOrNull { it.isAnnotatedWith(daggerSubcomponentBuilderFqName) }
        ?.let { factoryClass ->
          throw AnvilCompilationExceptionClassReference(
            classReference = factoryClass,
            message = "Within a class using @${ContributesSubcomponent::class.simpleName} you " +
              "must use $contributesSubcomponentFactoryFqName and not " +
              "$daggerSubcomponentBuilderFqName. Builders aren't supported.",
          )
        }

      val factories = innerClasses
        .filter { it.isAnnotatedWith(contributesSubcomponentFactoryFqName) }

      val factory = when (factories.size) {
        0 -> return
        1 -> factories[0]
        else -> throw AnvilCompilationExceptionClassReference(
          classReference = this,
          message = "Expected zero or one factory within $fqName.",
        )
      }

      if (!factory.isInterface() && !factory.isAbstract()) {
        throw AnvilCompilationExceptionClassReference(
          classReference = factory,
          message = "A factory must be an interface or an abstract class.",
        )
      }

      val returnType = factory.memberFunctions
        // filter by `isAbstract` even for interfaces,
        // otherwise we get `toString()`, `equals()`, and `hashCode()`.
        .singleOrNull { it.isAbstract() }
        ?.returnTypeWithGenericSubstitutionOrNull(factory)
        ?.asClassReference()

      if (returnType != this) {
        throw AnvilCompilationExceptionClassReference(
          classReference = factory,
          message = "A factory must have exactly one abstract function returning the " +
            "subcomponent $fqName.",
        )
      }
    }

    private fun ClassReference.checkUsesSameScope(
      scope: ClassReference,
      subcomponent: ClassReference,
    ) {
      annotations
        .filter { it.fqName == contributesSubcomponentFqName }
        .ifEmpty {
          throw AnvilCompilationExceptionClassReference(
            classReference = subcomponent,
            message = "Couldn't find the annotation @ContributesSubcomponent for $fqName.",
          )
        }
        .forEach { annotation ->
          val otherScope = annotation.scope()
          if (otherScope != scope) {
            throw AnvilCompilationExceptionClassReference(
              classReference = subcomponent,
              message = "${subcomponent.fqName} with scope ${scope.fqName} wants to replace " +
                "$fqName with scope ${otherScope.fqName}. The replacement must use the same scope.",
            )
          }
        }
    }
  }

  private fun createSpec(
    className: ClassName,
    parentScope: ClassName,
  ): FileSpec {
    val fileName = className.generateHintFileName("_", capitalizePackage = true)
    val classFqName = className.canonicalName
    val propertyName = classFqName.replace('.', '_')

    val spec =
      FileSpec.createAnvilSpec(HINT_PACKAGE, fileName) {
        addProperty(
          PropertySpec
            .builder(
              name = propertyName + REFERENCE_SUFFIX,
              type = KClass::class.asClassName().parameterizedBy(className),
            )
            .initializer("%T::class", className)
            .addModifiers(PUBLIC)
            .build(),
        )

        addProperty(
          PropertySpec
            .builder(
              name = propertyName + SCOPE_SUFFIX,
              type = KClass::class.asClassName().parameterizedBy(parentScope),
            )
            .initializer("%T::class", parentScope)
            .addModifiers(PUBLIC)
            .build(),
        )
      }
    return spec
  }
}
