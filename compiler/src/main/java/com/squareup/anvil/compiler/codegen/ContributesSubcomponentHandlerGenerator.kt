package com.squareup.anvil.compiler.codegen

import com.squareup.anvil.annotations.MergeSubcomponent
import com.squareup.anvil.compiler.ANVIL_SUBCOMPONENT_SUFFIX
import com.squareup.anvil.compiler.COMPONENT_PACKAGE_PREFIX
import com.squareup.anvil.compiler.ClassScanner
import com.squareup.anvil.compiler.HINT_SUBCOMPONENTS_PACKAGE_PREFIX
import com.squareup.anvil.compiler.api.AnvilContext
import com.squareup.anvil.compiler.api.CodeGenerator
import com.squareup.anvil.compiler.api.GeneratedFile
import com.squareup.anvil.compiler.api.createGeneratedFile
import com.squareup.anvil.compiler.contributesSubcomponentFqName
import com.squareup.anvil.compiler.internal.annotation
import com.squareup.anvil.compiler.internal.asClassName
import com.squareup.anvil.compiler.internal.buildFile
import com.squareup.anvil.compiler.internal.classesAndInnerClass
import com.squareup.anvil.compiler.internal.generateClassName
import com.squareup.anvil.compiler.internal.hasAnnotation
import com.squareup.anvil.compiler.internal.parentScope
import com.squareup.anvil.compiler.internal.safePackageString
import com.squareup.anvil.compiler.internal.scope
import com.squareup.anvil.compiler.mergeComponentFqName
import com.squareup.anvil.compiler.mergeModulesFqName
import com.squareup.anvil.compiler.mergeSubcomponentFqName
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeSpec
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.descriptorUtil.classId
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
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
        val generatedPackage = COMPONENT_PACKAGE_PREFIX +
          contribution.clazz.packageFqName.safePackageString(dotPrefix = true)

        val componentClassName = contribution.clazz.relativeClassName.generateClassName() +
          ANVIL_SUBCOMPONENT_SUFFIX

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
    override fun toString(): String {
      return "Contribution(class=$clazz, parentScope=$parentScope)"
    }
  }
}
