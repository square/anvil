package com.squareup.anvil.compiler

import com.squareup.anvil.annotations.ContributesTo
import com.squareup.anvil.annotations.MergeComponent
import com.squareup.anvil.annotations.MergeSubcomponent
import com.squareup.anvil.annotations.compat.MergeModules
import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.internal.annotations
import com.squareup.anvil.compiler.internal.argumentType
import com.squareup.anvil.compiler.internal.getAnnotationValue
import com.squareup.anvil.compiler.internal.parentScope
import com.squareup.anvil.compiler.internal.requireClassDescriptor
import com.squareup.anvil.compiler.internal.safePackageString
import com.squareup.anvil.compiler.internal.scope
import com.squareup.anvil.compiler.internal.singleOrEmpty
import com.squareup.anvil.compiler.internal.toType
import dagger.Component
import dagger.Module
import dagger.Subcomponent
import org.jetbrains.kotlin.backend.common.serialization.findPackage
import org.jetbrains.kotlin.codegen.ImplementationBodyCodegen
import org.jetbrains.kotlin.codegen.asmType
import org.jetbrains.kotlin.codegen.extensions.ExpressionCodegenExtension
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.EffectiveVisibility.Public
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.descriptors.effectiveVisibility
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.constants.ArrayValue
import org.jetbrains.kotlin.resolve.constants.ConstantValue
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.resolve.descriptorUtil.parentsWithSelf
import org.jetbrains.org.objectweb.asm.AnnotationVisitor
import org.jetbrains.org.objectweb.asm.Type
import kotlin.reflect.KClass

/**
 * Finds all contributed Dagger modules and adds them to Dagger components annotated with
 * `@MergeComponent` or `@MergeSubcomponent`. This class is responsible for generating the
 * `@Component` and `@Subcomponent` annotation required for Dagger.
 */
internal class ModuleMerger(
  private val classScanner: ClassScanner
) : ExpressionCodegenExtension {

  override fun generateClassSyntheticParts(codegen: ImplementationBodyCodegen) {
    val holder = AnnotationHolder.create(codegen.descriptor) ?: return
    val (mergeAnnotation, annotationClass, daggerClass, daggerFqName, modulesKeyword) = holder

    if (codegen.descriptor.annotations(daggerFqName).isNotEmpty()) {
      throw AnvilCompilationException(
        codegen.descriptor,
        "When using @${annotationClass.simpleName} it's not allowed to annotate the same " +
          "class with @${daggerClass.simpleName}. The Dagger annotation will be generated."
      )
    }

    val module = codegen.descriptor.module
    val scope = mergeAnnotation.scope(module)
    val scopeFqName = scope.fqNameSafe

    val predefinedModules =
      (mergeAnnotation.getAnnotationValue(modulesKeyword) as? ArrayValue)
        ?.value
        ?.map { it.toType(codegen) }

    val anvilModuleName = createAnvilModuleName(codegen.descriptor)

    val modules = classScanner
      .findContributedHints(
        module = module,
        annotation = contributesToFqName,
      )
      .filter {
        // We generate a Dagger module for each merged component. We use Anvil itself to
        // contribute this generated module. It's possible that there are multiple components
        // merging the same scope or the same scope is merged in different Gradle modules which
        // depend on each other. This would cause duplicate bindings, because the generated
        // modules contain the same bindings and are contributed to the same scope. To avoid this
        // issue we filter all generated Anvil modules except for the one that was generated for
        // this specific class.
        val fqName = it.fqName
        !fqName.isAnvilModule() || fqName == anvilModuleName
      }
      .filter { it.isContributedToScope(scopeFqName) }
      .filter { hint ->
        val moduleAnnotation = hint.descriptor.annotations(daggerModuleFqName).singleOrEmpty()
        val mergeModulesAnnotation = hint.descriptor
          .annotations(mergeModulesFqName).singleOrEmpty()

        if (!DescriptorUtils.isInterface(hint.descriptor) &&
          moduleAnnotation == null &&
          mergeModulesAnnotation == null
        ) {
          throw AnvilCompilationException(
            classDescriptor = hint.descriptor,
            message = "${hint.fqName} is annotated with " +
              "@${ContributesTo::class.simpleName}, but this class is neither an interface " +
              "nor a Dagger module. Did you forget to add @${Module::class.simpleName}?",
          )
        }

        moduleAnnotation != null || mergeModulesAnnotation != null
      }
      .onEach { hint ->
        if (hint.descriptor.effectiveVisibility() !is Public) {
          throw AnvilCompilationException(
            classDescriptor = hint.descriptor,
            message = "${hint.fqName} is contributed to the Dagger graph, but the " +
              "module is not public. Only public modules are supported."
          )
        }
      }
      // Convert the sequence to a list to avoid iterating it twice. We use the result twice
      // for replaced classes and the final result.
      .toList()

    val excludedModules = (mergeAnnotation.getAnnotationValue("exclude") as? ArrayValue)
      ?.value
      ?.map { constantValue ->
        val argumentType = constantValue.argumentType(module)
        val excludedClass = argumentType.requireClassDescriptor()

        val scopes = excludedClass.annotations(contributesToFqName).map { it.scope(module) } +
          excludedClass.annotations(contributesBindingFqName).map { it.scope(module) } +
          excludedClass.annotations(contributesMultibindingFqName).map { it.scope(module) } +
          excludedClass.annotations(contributesSubcomponentFqName).map { it.parentScope(module) }

        // Verify that the replaced classes use the same scope.
        val contributesToOurScope = scopes.any { it.fqNameSafe == scopeFqName }

        if (!contributesToOurScope) {
          throw AnvilCompilationException(
            message = "${codegen.descriptor.fqNameSafe} with scope $scopeFqName wants to exclude " +
              "${excludedClass.fqNameSafe}, but the excluded class isn't contributed to the " +
              "same scope.",
            classDescriptor = codegen.descriptor
          )
        }

        argumentType.asmType(codegen.typeMapper)
      }
      ?: emptyList()

    val replacedModules = modules
      .filter { hint ->
        // Ignore replaced modules or bindings specified by excluded modules.
        hint.descriptor.defaultType.asmType(codegen.typeMapper) !in excludedModules
      }
      .flatMap { hint ->
        hint.contributedAnnotation(scopeFqName).replaces(module)
          .map { classDescriptorForReplacement ->
            // Verify has @Module annotation. It doesn't make sense for a Dagger module to
            // replace a non-Dagger module.
            if (classDescriptorForReplacement.annotations(daggerModuleFqName).isEmpty() &&
              classDescriptorForReplacement.annotations(contributesBindingFqName).isEmpty() &&
              classDescriptorForReplacement.annotations(contributesMultibindingFqName).isEmpty()
            ) {
              throw AnvilCompilationException(
                message = "${hint.fqName} wants to replace " +
                  "${classDescriptorForReplacement.fqNameSafe}, but the class being " +
                  "replaced is not a Dagger module or binding.",
                classDescriptor = hint.descriptor
              )
            }

            checkSameScope(hint, classDescriptorForReplacement, module, scopeFqName)

            classDescriptorForReplacement.defaultType.asmType(codegen.typeMapper)
          }
      }

    fun replacedModulesByContributedBinding(
      annotationFqName: FqName
    ): Sequence<Type> {
      return classScanner
        .findContributedHints(
          module = module,
          annotation = annotationFqName,
        )
        .flatMap { hint ->
          hint.contributedAnnotations()
            .filter { it.scope(module) == scope }
            .flatMap { it.replaces(module) }
            .map { classDescriptorForReplacement ->
              checkSameScope(hint, classDescriptorForReplacement, module, scopeFqName)
              classDescriptorForReplacement.defaultType.asmType(codegen.typeMapper)
            }
        }
    }

    val replacedModulesByContributedBindings = replacedModulesByContributedBinding(
      annotationFqName = contributesBindingFqName
    )

    val replacedModulesByContributedMultibindings = replacedModulesByContributedBinding(
      annotationFqName = contributesMultibindingFqName
    )

    if (predefinedModules != null) {
      val intersect = predefinedModules.intersect(excludedModules.toSet())
      if (intersect.isNotEmpty()) {
        throw AnvilCompilationException(
          codegen.descriptor,
          "${codegen.descriptor.name} includes and excludes modules " +
            "at the same time: ${intersect.joinToString { it.className }}"
        )
      }
    }

    val contributedModules = modules
      .asSequence()
      .map { codegen.typeMapper.mapType(it.descriptor) }
      .minus(replacedModules.toSet())
      .minus(replacedModulesByContributedBindings.toSet())
      .minus(replacedModulesByContributedMultibindings.toSet())
      .minus(excludedModules.toSet())
      .distinct()

    codegen.v
      .newAnnotation("L${daggerClass.java.canonicalName.replace('.', '/')};", true)
      .use {
        visitArray(modulesKeyword)
          .use {
            predefinedModules?.forEach { visit(modulesKeyword, it) }
            contributedModules.forEach { visit(modulesKeyword, it) }
          }

        if (holder.isComponent) {
          copyArrayValue(codegen, mergeAnnotation, "dependencies")
        }

        if (holder.isModule) {
          copyArrayValue(codegen, mergeAnnotation, "subcomponents")
        }
      }
  }

  private fun AnnotationVisitor.copyArrayValue(
    codegen: ImplementationBodyCodegen,
    annotation: AnnotationDescriptor,
    name: String
  ) {
    val predefinedValues =
      (annotation.getAnnotationValue(name) as? ArrayValue)
        ?.value
        ?.map { it.toType(codegen) }

    visitArray(name).use {
      predefinedValues?.forEach { visit(name, it) }
    }
  }

  private fun ConstantValue<*>.toType(codegen: ImplementationBodyCodegen): Type {
    return toType(codegen.descriptor.module, codegen.typeMapper)
  }

  private inline fun AnnotationVisitor.use(block: AnnotationVisitor.() -> Unit) {
    block(this)
    visitEnd()
  }

  private fun createAnvilModuleName(classDescriptor: ClassDescriptor): FqName {
    val name = "$MODULE_PACKAGE_PREFIX." +
      classDescriptor.findPackage().fqName.safePackageString() +
      classDescriptor.parentsWithSelf
        .filterIsInstance<ClassDescriptor>()
        .toList()
        .reversed()
        .joinToString(separator = "", postfix = ANVIL_MODULE_SUFFIX) {
          it.fqNameSafe
            .shortName()
            .asString()
        }
    return FqName(name)
  }

  private fun checkSameScope(
    hint: ContributedHint,
    classForReplacement: ClassDescriptor,
    module: ModuleDescriptor,
    scopeFqName: FqName
  ) {
    val scopes = classForReplacement.annotations(contributesToFqName).map { it.scope(module) } +
      classForReplacement.annotations(contributesBindingFqName).map { it.scope(module) } +
      classForReplacement.annotations(contributesMultibindingFqName).map { it.scope(module) }

    val contributesToOurScope = scopes.any { it.fqNameSafe == scopeFqName }

    if (!contributesToOurScope) {
      throw AnvilCompilationException(
        classDescriptor = hint.descriptor,
        message = "${hint.fqName} with scope $scopeFqName wants to replace " +
          "${classForReplacement.fqNameSafe}, but the replaced class isn't " +
          "contributed to the same scope."
      )
    }
  }
}

@Suppress("DataClassPrivateConstructor")
private data class AnnotationHolder private constructor(
  val annotationDescriptor: AnnotationDescriptor,
  val annotationClass: KClass<*>,
  val daggerClass: KClass<*>,
  val daggerFqName: FqName,
  val modulesKeyword: String
) {
  val isComponent = annotationClass == MergeComponent::class
  val isModule = annotationClass == MergeModules::class

  companion object {
    fun create(descriptor: ClassDescriptor): AnnotationHolder? {
      descriptor.annotations(mergeComponentFqName).singleOrEmpty()
        ?.let {
          return AnnotationHolder(
            it,
            MergeComponent::class,
            Component::class,
            daggerComponentFqName,
            "modules"
          )
        }
      descriptor.annotations(mergeSubcomponentFqName).singleOrEmpty()
        ?.let {
          return AnnotationHolder(
            it,
            MergeSubcomponent::class,
            Subcomponent::class,
            daggerSubcomponentFqName,
            "modules"
          )
        }
      descriptor.annotations(mergeModulesFqName).singleOrEmpty()
        ?.let {
          return AnnotationHolder(
            it,
            MergeModules::class,
            Module::class,
            daggerModuleFqName,
            "includes"
          )
        }
      return null
    }
  }
}
