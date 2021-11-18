package com.squareup.anvil.compiler.codegen

import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.anvil.annotations.MergeSubcomponent
import com.squareup.anvil.compiler.ANVIL_SUBCOMPONENT_SUFFIX
import com.squareup.anvil.compiler.COMPONENT_PACKAGE_PREFIX
import com.squareup.anvil.compiler.ClassScanner
import com.squareup.anvil.compiler.HINT_SUBCOMPONENTS_PACKAGE_PREFIX
import com.squareup.anvil.compiler.PARENT_COMPONENT
import com.squareup.anvil.compiler.SUBCOMPONENT_FACTORY
import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.api.AnvilContext
import com.squareup.anvil.compiler.api.CodeGenerator
import com.squareup.anvil.compiler.api.GeneratedFile
import com.squareup.anvil.compiler.api.createGeneratedFile
import com.squareup.anvil.compiler.contributesSubcomponentFactoryFqName
import com.squareup.anvil.compiler.contributesSubcomponentFqName
import com.squareup.anvil.compiler.contributesToFqName
import com.squareup.anvil.compiler.internal.ClassReference
import com.squareup.anvil.compiler.internal.annotation
import com.squareup.anvil.compiler.internal.annotationOrNull
import com.squareup.anvil.compiler.internal.argumentType
import com.squareup.anvil.compiler.internal.asClassName
import com.squareup.anvil.compiler.internal.buildFile
import com.squareup.anvil.compiler.internal.classesAndInnerClass
import com.squareup.anvil.compiler.internal.daggerScopes
import com.squareup.anvil.compiler.internal.findAnnotation
import com.squareup.anvil.compiler.internal.findAnnotationArgument
import com.squareup.anvil.compiler.internal.generateClassName
import com.squareup.anvil.compiler.internal.getAnnotationValue
import com.squareup.anvil.compiler.internal.hasAnnotation
import com.squareup.anvil.compiler.internal.parentScope
import com.squareup.anvil.compiler.internal.requireClassDescriptor
import com.squareup.anvil.compiler.internal.requireClassId
import com.squareup.anvil.compiler.internal.safePackageString
import com.squareup.anvil.compiler.internal.scope
import com.squareup.anvil.compiler.internal.toClassReference
import com.squareup.anvil.compiler.mergeComponentFqName
import com.squareup.anvil.compiler.mergeModulesFqName
import com.squareup.anvil.compiler.mergeSubcomponentFqName
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier.ABSTRACT
import com.squareup.kotlinpoet.KModifier.OVERRIDE
import com.squareup.kotlinpoet.TypeSpec
import dagger.Subcomponent
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities.PUBLIC
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtCollectionLiteralExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.constants.ArrayValue
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter.Companion.CLASSIFIERS
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter.Companion.FUNCTIONS
import java.io.File

/**
 * Looks for `@MergeComponent`, `@MergeSubcomponent` or `@MergeModules` annotations and generates
 * the actual contributed subcomponents that specified these scopes as parent scope, e.g.
 *
 * ```
 * @MergeComponent(Unit::class)
 * interface ComponentInterface
 *
 * @ContributesSubcomponent(Any::class, parentScope = Unit::class)
 * interface SubcomponentInterface
 * ```
 * For this code snippet the code generator would generate:
 * ```
 * @MergeSubcomponent(Any::class)
 * interface SubcomponentInterfaceAnvilSubcomponent
 * ```
 */
internal class ContributesSubcomponentHandlerGenerator(
  private val classScanner: ClassScanner
) : CodeGenerator {

  private val triggers = mutableListOf<Trigger>()
  private val contributions = mutableListOf<Contribution>()

  override fun isApplicable(context: AnvilContext): Boolean =
    throw NotImplementedError(
      "This should not actually be checked as we instantiate this class manually."
    )

  override fun generateCode(
    codeGenDir: File,
    module: ModuleDescriptor,
    projectFiles: Collection<KtFile>
  ): Collection<GeneratedFile> {

    // Find new @MergeComponent (and similar triggers) that would cause generating new code.
    val newTriggers = projectFiles
      .classesAndInnerClass(module)
      .mapNotNull { clazz ->
        val annotation = generationTrigger.firstOrNull { trigger ->
          clazz.hasAnnotation(trigger, module)
        } ?: return@mapNotNull null

        val exclusions = clazz.excludeOrNull(module, annotation) ?: emptyList()

        Trigger(clazz, clazz.scope(annotation, module), exclusions)
      }
      .also {
        triggers += it
      }

    // If there is a new trigger, then find all contributed subcomponents from precompiled
    // dependencies for this scope and generate the necessary code.
    contributions += newTriggers
      .flatMap { trigger ->
        classScanner
          .findContributedClasses(
            module = module,
            packageName = HINT_SUBCOMPONENTS_PACKAGE_PREFIX,
            annotation = contributesSubcomponentFqName,
            // Don't use trigger.scope, because it refers to the parent scope of the
            // @ContributesSubcomponent annotation.
            scope = null
          )
          .mapNotNull { descriptor ->
            val annotation = descriptor.annotation(contributesSubcomponentFqName)

            val parentScope = annotation.parentScope(module).fqNameSafe
            if (parentScope != trigger.scope) return@mapNotNull null

            val modules = (annotation.getAnnotationValue("modules") as? ArrayValue)
              ?.value
              ?.map { it.argumentType(module).fqName() }
              ?: emptyList()

            val exclude = (annotation.getAnnotationValue("exclude") as? ArrayValue)
              ?.value
              ?.map { it.argumentType(module).fqName() }
              ?: emptyList()

            Contribution(
              classReference = descriptor.toClassReference(),
              clazz = descriptor.requireClassId(),
              scope = annotation.scope(module).fqNameSafe,
              parentScope = parentScope,
              modules = modules,
              exclude = exclude
            )
          }
      }

    // Find new contributed subcomponents in this module. If there's a trigger for them, then we
    // also need to generate code for them.
    contributions += projectFiles
      .classesAndInnerClass(module)
      .mapNotNull { clazz ->
        val annotation = clazz.findAnnotation(contributesSubcomponentFqName, module)
          ?: return@mapNotNull null

        val modules = annotation
          .findAnnotationArgument<KtCollectionLiteralExpression>("modules", 2)
          ?.toFqNames(module)
          ?: emptyList()

        val exclude = annotation
          .findAnnotationArgument<KtCollectionLiteralExpression>("exclude", 3)
          ?.toFqNames(module)
          ?: emptyList()

        Contribution(
          classReference = clazz.toClassReference(),
          clazz = requireNotNull(clazz.getClassId()),
          scope = clazz.scope(contributesSubcomponentFqName, module),
          parentScope = clazz.parentScope(contributesSubcomponentFqName, module),
          modules = modules,
          exclude = exclude
        )
      }

    val newContributions = contributions
      .filterNot { contribution ->
        val allMatchingTriggers = triggers.filter { it.scope == contribution.parentScope }
        val excludedInAllTriggers = allMatchingTriggers.all { trigger ->
          contribution.clazzFqName in trigger.exclusions
        }

        // If the contributed subcomponent is excluded in all triggers, then we don't want to
        // generate any code for it and not add it to our newContributions list.
        excludedInAllTriggers
      }

    contributions -= newContributions.toSet()

    return newContributions
      .map { contribution ->
        val generatedPackage = contribution.generatedPackage
        val componentClassName = contribution.generatedSubcomponentClassName
        val factoryClass = findFactoryClass(contribution, module)

        val content = FileSpec.buildFile(generatedPackage, componentClassName) {
          TypeSpec
            .interfaceBuilder(componentClassName)
            .addSuperinterface(contribution.clazz.asClassName())
            .addAnnotation(
              AnnotationSpec
                .builder(MergeSubcomponent::class)
                .addMember("scope = %T::class", contribution.scope.asClassName(module))
                .apply {
                  fun addClassArrayMember(
                    name: String,
                    fqNames: List<FqName>
                  ) {
                    if (fqNames.isNotEmpty()) {
                      val classes = fqNames.map { it.asClassName(module) }
                      val template = classes
                        .joinToString(prefix = "[", postfix = "]") { "%T::class" }

                      addMember("$name = $template", *classes.toTypedArray())
                    }
                  }

                  addClassArrayMember("modules", contribution.modules)
                  addClassArrayMember("exclude", contribution.exclude)
                }
                .build()
            )
            .addAnnotations(contribution.classReference.daggerScopes(module))
            .apply {
              if (factoryClass != null) {
                addType(generateFactory(factoryClass.originalDescriptor, contribution, module))
              }
            }
            .addType(generateParentComponent(contribution, module, factoryClass))
            .build()
            .also { addType(it) }
        }

        createGeneratedFile(
          codeGenDir = codeGenDir,
          packageName = generatedPackage,
          fileName = componentClassName,
          content = content
        )
      }
      .toList()
  }

  private fun generateFactory(
    factoryDescriptor: ClassDescriptor,
    contribution: Contribution,
    module: ModuleDescriptor
  ): TypeSpec {
    val superclass = factoryDescriptor.asClassName()

    val builder = if (DescriptorUtils.isInterface(factoryDescriptor)) {
      TypeSpec
        .interfaceBuilder(SUBCOMPONENT_FACTORY)
        .addSuperinterface(superclass)
    } else {
      TypeSpec
        .classBuilder(SUBCOMPONENT_FACTORY)
        .addModifiers(ABSTRACT)
        .superclass(superclass)
    }

    return builder
      .addAnnotation(Subcomponent.Factory::class)
      .addAnnotation(
        // This will allow injecting the factory instance.
        AnnotationSpec
          .builder(ContributesBinding::class)
          .addMember("scope = %T::class", contribution.parentScope.asClassName(module))
          .addMember("boundType = %T::class", superclass)
          .build()
      )
      .build()
  }

  private fun generateParentComponent(
    contribution: Contribution,
    module: ModuleDescriptor,
    factoryClass: FactoryClassHolder?,
  ): TypeSpec {
    val parentComponentInterface = findParentComponentInterface(
      contribution, module, factoryClass?.originalDescriptor?.fqNameSafe
    )

    return TypeSpec
      .interfaceBuilder(PARENT_COMPONENT)
      .apply {
        if (parentComponentInterface != null) {
          addSuperinterface(parentComponentInterface.componentInterface)
        }
      }
      .addAnnotation(
        AnnotationSpec
          .builder(ContributesTo::class)
          .addMember("%T::class", contribution.parentScope.asClassName(module))
          .apply {
            if (parentComponentInterface != null) {
              addMember(
                "replaces = [%T::class]", parentComponentInterface.componentInterface
              )
            }
          }
          .build()
      )
      .addFunction(
        FunSpec
          .builder(
            name = parentComponentInterface
              ?.functionName
              ?: factoryClass?.let { "create${it.originalDescriptor.name}" }
              ?: "create${contribution.generatedSubcomponentClassName}"
          )
          .addModifiers(ABSTRACT)
          .apply {
            if (parentComponentInterface != null) {
              addModifiers(OVERRIDE)
            }

            if (factoryClass != null) {
              returns(factoryClass.generatedFactoryName)
            } else {
              returns(
                ClassName(
                  contribution.generatedPackage,
                  contribution.generatedSubcomponentClassName
                )
              )
            }
          }
          .build()
      )
      .build()
  }

  private fun findParentComponentInterface(
    contribution: Contribution,
    module: ModuleDescriptor,
    factoryClass: FqName?
  ): ParentComponentInterfaceHolder? {
    val contributionFqName = contribution.clazzFqName
    val contributionDescriptor = contributionFqName.requireClassDescriptor(module)

    val contributedInnerComponentInterfaces = contributionDescriptor.unsubstitutedMemberScope
      .getContributedDescriptors(kindFilter = CLASSIFIERS)
      .filterIsInstance<ClassDescriptor>()
      .filter { DescriptorUtils.isInterface(it) }
      .filter {
        val annotation = it.annotationOrNull(contributesToFqName, contribution.parentScope)
        annotation != null
      }

    val componentInterface = when (contributedInnerComponentInterfaces.size) {
      0 -> return null
      1 -> contributedInnerComponentInterfaces[0]
      else -> throw AnvilCompilationException(
        classDescriptor = contributionDescriptor,
        message = "Expected zero or one parent component interface within " +
          "${contributionDescriptor.fqNameSafe} being contributed to the parent scope."
      )
    }

    val functions = componentInterface.unsubstitutedMemberScope
      .getContributedDescriptors(kindFilter = FUNCTIONS)
      .filterIsInstance<FunctionDescriptor>()
      .filter { it.modality == Modality.ABSTRACT && it.visibility == PUBLIC }
      .filter {
        val returnType = it.returnType?.fqNameOrNull() ?: return@filter false
        returnType == contributionFqName || (factoryClass != null && returnType == factoryClass)
      }

    val function = when (functions.size) {
      0 -> return null
      1 -> functions[0]
      else -> throw AnvilCompilationException(
        classDescriptor = componentInterface,
        message = "Expected zero or one function returning the subcomponent $contributionFqName."
      )
    }

    return ParentComponentInterfaceHolder(componentInterface, function)
  }

  private fun findFactoryClass(
    contribution: Contribution,
    module: ModuleDescriptor
  ): FactoryClassHolder? {
    val contributionFqName = contribution.clazzFqName
    val contributionDescriptor = contributionFqName.requireClassDescriptor(module)

    val contributedFactories = contributionDescriptor.unsubstitutedMemberScope
      .getContributedDescriptors(kindFilter = CLASSIFIERS)
      .filterIsInstance<ClassDescriptor>()
      .filter { it.annotations.hasAnnotation(contributesSubcomponentFactoryFqName) }
      .onEach { factory ->
        if (!DescriptorUtils.isInterface(factory) && factory.modality != Modality.ABSTRACT) {
          throw AnvilCompilationException(
            classDescriptor = factory,
            message = "A factory must be an interface or an abstract class."
          )
        }

        val createComponentFunctions = factory.unsubstitutedMemberScope
          .getContributedDescriptors(kindFilter = FUNCTIONS)
          .filterIsInstance<FunctionDescriptor>()
          .filter { it.modality == Modality.ABSTRACT }
          .filter { it.returnType?.fqNameOrNull() == contributionFqName }

        if (createComponentFunctions.size != 1) {
          throw AnvilCompilationException(
            classDescriptor = factory,
            message = "A factory must have exactly one abstract function returning the " +
              "subcomponent $contributionFqName."
          )
        }
      }

    val descriptor = when (contributedFactories.size) {
      0 -> return null
      1 -> contributedFactories[0]
      else -> throw AnvilCompilationException(
        classDescriptor = contributionDescriptor,
        message = "Expected zero or one factory within ${contributionDescriptor.fqNameSafe}."
      )
    }

    return FactoryClassHolder(
      originalDescriptor = descriptor,
      generatedFactoryName = ClassName(
        packageName = contribution.generatedPackage,
        simpleNames = listOf(contribution.generatedSubcomponentClassName, SUBCOMPONENT_FACTORY)
      )
    )
  }

  private companion object {
    val generationTrigger = setOf(
      mergeComponentFqName,
      mergeSubcomponentFqName,
      // Note that we don't include @MergeInterfaces, because we would potentially generate
      // components twice. @MergeInterfaces and @MergeModules are doing separately what
      // @MergeComponent is doing at once.
      mergeModulesFqName,
    )
  }

  private class Trigger(
    val clazz: KtClassOrObject,
    val scope: FqName,
    val exclusions: List<FqName>
  ) {
    override fun toString(): String {
      return "Trigger(clazz=$clazz, scope=$scope)"
    }
  }

  private class Contribution(
    val classReference: ClassReference,
    val clazz: ClassId,
    val scope: FqName,
    val parentScope: FqName,
    val modules: List<FqName>,
    val exclude: List<FqName>
  ) {
    val clazzFqName = clazz.asSingleFqName()

    val generatedPackage: String
    val generatedSubcomponentClassName: String

    init {
      val generatedAnvilSubcomponent = clazz.generatedAnvilSubcomponent()
      generatedPackage = generatedAnvilSubcomponent.packageFqName.asString()
      generatedSubcomponentClassName = generatedAnvilSubcomponent.relativeClassName.asString()
    }

    override fun toString(): String {
      return "Contribution(class=$clazz, parentScope=$parentScope)"
    }
  }

  private class ParentComponentInterfaceHolder(
    componentInterface: ClassDescriptor,
    function: FunctionDescriptor
  ) {
    val componentInterface = componentInterface.asClassName()
    val functionName = function.name.asString()
  }

  private class FactoryClassHolder(
    val originalDescriptor: ClassDescriptor,
    val generatedFactoryName: ClassName
  )
}

/**
 * Returns the Anvil subcomponent that will be generated for a class annotated with
 * `ContributesSubcomponent`.
 */
internal fun ClassId.generatedAnvilSubcomponent(): ClassId {
  val packageFqName = COMPONENT_PACKAGE_PREFIX +
    packageFqName.safePackageString(dotPrefix = true, dotSuffix = false)
  val relativeClassName = relativeClassName.generateClassName() + ANVIL_SUBCOMPONENT_SUFFIX
  return ClassId(FqName(packageFqName), FqName(relativeClassName), false)
}
