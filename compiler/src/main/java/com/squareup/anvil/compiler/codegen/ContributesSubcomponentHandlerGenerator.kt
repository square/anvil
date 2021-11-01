package com.squareup.anvil.compiler.codegen

import com.squareup.anvil.annotations.ContributesTo
import com.squareup.anvil.annotations.MergeSubcomponent
import com.squareup.anvil.compiler.ANVIL_SUBCOMPONENT_SUFFIX
import com.squareup.anvil.compiler.COMPONENT_PACKAGE_PREFIX
import com.squareup.anvil.compiler.ClassScanner
import com.squareup.anvil.compiler.HINT_SUBCOMPONENTS_PACKAGE_PREFIX
import com.squareup.anvil.compiler.PARENT_COMPONENT
import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.api.AnvilContext
import com.squareup.anvil.compiler.api.CodeGenerator
import com.squareup.anvil.compiler.api.GeneratedFile
import com.squareup.anvil.compiler.api.createGeneratedFile
import com.squareup.anvil.compiler.contributesSubcomponentFqName
import com.squareup.anvil.compiler.contributesToFqName
import com.squareup.anvil.compiler.internal.annotation
import com.squareup.anvil.compiler.internal.annotationOrNull
import com.squareup.anvil.compiler.internal.asClassName
import com.squareup.anvil.compiler.internal.buildFile
import com.squareup.anvil.compiler.internal.classesAndInnerClass
import com.squareup.anvil.compiler.internal.generateClassName
import com.squareup.anvil.compiler.internal.hasAnnotation
import com.squareup.anvil.compiler.internal.parentScope
import com.squareup.anvil.compiler.internal.requireClassDescriptor
import com.squareup.anvil.compiler.internal.safePackageString
import com.squareup.anvil.compiler.internal.scope
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
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities.PUBLIC
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.descriptorUtil.classId
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

        Trigger(clazz, annotation, clazz.scope(annotation, module))
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

            Contribution(
              clazz = requireNotNull(descriptor.classId),
              scope = annotation.scope(module).fqNameSafe,
              parentScope = parentScope
            )
          }
      }

    // Find new contributed subcomponents in this module. If there's a trigger for them, then we
    // also need to generate code for them.
    contributions += projectFiles
      .classesAndInnerClass(module)
      .mapNotNull { clazz ->
        if (!clazz.hasAnnotation(contributesSubcomponentFqName, module)) return@mapNotNull null

        Contribution(
          clazz = requireNotNull(clazz.getClassId()),
          scope = clazz.scope(contributesSubcomponentFqName, module),
          parentScope = clazz.parentScope(contributesSubcomponentFqName, module)
        )
      }

    val triggerScopes = triggers.map { it.scope }
    val newContributions = contributions
      .filter { contribution ->
        contribution.parentScope in triggerScopes
      }

    contributions -= newContributions.toSet()

    return newContributions
      .map { contribution ->
        val generatedPackage = contribution.generatedPackage
        val componentClassName = contribution.generatedSubcomponentClassName

        val content = FileSpec.buildFile(generatedPackage, componentClassName) {
          TypeSpec
            .interfaceBuilder(componentClassName)
            .addSuperinterface(contribution.clazz.asClassName())
            .addAnnotation(
              AnnotationSpec
                .builder(MergeSubcomponent::class)
                .addMember("%T::class", contribution.scope.asClassName(module))
                .build()
            )
            .addType(generateParentComponent(contribution, module))
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

  private fun generateParentComponent(
    contribution: Contribution,
    module: ModuleDescriptor
  ): TypeSpec {
    val parentComponentInterface = findParentComponentInterface(contribution, module)

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
              ?: "create${contribution.generatedSubcomponentClassName}"
          )
          .addModifiers(ABSTRACT)
          .apply {
            if (parentComponentInterface != null) {
              addModifiers(OVERRIDE)
            }
          }
          .returns(
            ClassName(contribution.generatedPackage, contribution.generatedSubcomponentClassName)
          )
          .build()
      )
      .build()
  }

  private fun findParentComponentInterface(
    contribution: Contribution,
    module: ModuleDescriptor
  ): ParentComponentInterfaceHolder? {
    val contributionFqName = contribution.clazz.asSingleFqName()
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
      .asSequence()
      .filterIsInstance<FunctionDescriptor>()
      .filter { it.modality == Modality.ABSTRACT && it.visibility == PUBLIC }
      .filter { it.returnType?.fqNameOrNull() == contributionFqName }
      .toList()

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
    val annotation: FqName,
    val scope: FqName
  ) {
    override fun toString(): String {
      return "Trigger(clazz=$clazz, scope=$scope)"
    }
  }

  private class Contribution(
    val clazz: ClassId,
    val scope: FqName,
    val parentScope: FqName,
    // TODO: modules, excludes
  ) {
    val generatedPackage = COMPONENT_PACKAGE_PREFIX +
      clazz.packageFqName.safePackageString(dotPrefix = true)

    val generatedSubcomponentClassName = clazz.relativeClassName.generateClassName() +
      ANVIL_SUBCOMPONENT_SUFFIX

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
}
