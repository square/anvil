package com.squareup.anvil.compiler

import com.squareup.anvil.annotations.ContributesTo
import com.squareup.anvil.annotations.MergeComponent
import com.squareup.anvil.annotations.MergeSubcomponent
import com.squareup.anvil.annotations.compat.MergeModules
import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.codegen.generatedAnvilSubcomponent
import com.squareup.anvil.compiler.internal.annotation
import com.squareup.anvil.compiler.internal.annotationOrNull
import com.squareup.anvil.compiler.internal.argumentType
import com.squareup.anvil.compiler.internal.classDescriptorOrNull
import com.squareup.anvil.compiler.internal.getAnnotationValue
import com.squareup.anvil.compiler.internal.parentScope
import com.squareup.anvil.compiler.internal.reference.replaces
import com.squareup.anvil.compiler.internal.reference.scope
import com.squareup.anvil.compiler.internal.reference.toClassReference
import com.squareup.anvil.compiler.internal.requireClassDescriptor
import com.squareup.anvil.compiler.internal.requireClassId
import com.squareup.anvil.compiler.internal.requireFqName
import com.squareup.anvil.compiler.internal.safePackageString
import com.squareup.anvil.compiler.internal.scope
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
import org.jetbrains.kotlin.name.Name
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
    val thisDescriptor = codegen.descriptor
    val holder = AnnotationHolder.create(thisDescriptor) ?: return
    val (mergeAnnotation, annotationClass, daggerClass, daggerFqName, modulesKeyword) = holder

    if (thisDescriptor.annotationOrNull(daggerFqName) != null) {
      throw AnvilCompilationException(
        thisDescriptor,
        "When using @${annotationClass.simpleName} it's not allowed to annotate the same " +
          "class with @${daggerClass.simpleName}. The Dagger annotation will be generated."
      )
    }

    val module = thisDescriptor.module
    val scope = mergeAnnotation.scope(module)
    val scopeFqName = scope.fqNameSafe

    val predefinedModules =
      (mergeAnnotation.getAnnotationValue(modulesKeyword) as? ArrayValue)
        ?.value
        ?.map { it.toType(codegen) }

    val anvilModuleName = createAnvilModuleName(thisDescriptor)

    val modules = classScanner
      .findContributedClasses(
        module = module,
        packageName = HINT_CONTRIBUTES_PACKAGE_PREFIX,
        annotation = contributesToFqName,
        scope = scopeFqName
      )
      .filter {
        // We generate a Dagger module for each merged component. We use Anvil itself to
        // contribute this generated module. It's possible that there are multiple components
        // merging the same scope or the same scope is merged in different Gradle modules which
        // depend on each other. This would cause duplicate bindings, because the generated
        // modules contain the same bindings and are contributed to the same scope. To avoid this
        // issue we filter all generated Anvil modules except for the one that was generated for
        // this specific class.
        val fqName = it.fqNameSafe
        !fqName.isAnvilModule() || fqName == anvilModuleName
      }
      .mapNotNull {
        val contributesAnnotation =
          it.annotationOrNull(contributesToFqName, scope = scopeFqName)
            ?: return@mapNotNull null
        it to contributesAnnotation
      }
      .filter { (classDescriptor, _) ->
        val moduleAnnotation = classDescriptor.annotationOrNull(daggerModuleFqName)
        val mergeModulesAnnotation = classDescriptor.annotationOrNull(mergeModulesFqName)
        if (!DescriptorUtils.isInterface(classDescriptor) &&
          moduleAnnotation == null &&
          mergeModulesAnnotation == null
        ) {
          throw AnvilCompilationException(
            classDescriptor,
            "${classDescriptor.fqNameSafe} is annotated with " +
              "@${ContributesTo::class.simpleName}, but this class is neither an interface " +
              "nor a Dagger module. Did you forget to add @${Module::class.simpleName}?"
          )
        }

        moduleAnnotation != null || mergeModulesAnnotation != null
      }
      .onEach { (classDescriptor, _) ->
        if (classDescriptor.effectiveVisibility() !is Public) {
          throw AnvilCompilationException(
            classDescriptor,
            "${classDescriptor.fqNameSafe} is contributed to the Dagger graph, but the " +
              "module is not public. Only public modules are supported."
          )
        }
      }
      // Convert the sequence to a list to avoid iterating it twice. We use the result twice
      // for replaced classes and the final result.
      .toList()

    val excludedModules = (mergeAnnotation.getAnnotationValue("exclude") as? ArrayValue)
      ?.value
      ?.map {
        val argumentType = it.argumentType(module)
        val classDescriptorForExclusion = argumentType.requireClassDescriptor()

        val contributesToAnnotation = classDescriptorForExclusion
          .annotationOrNull(contributesToFqName)
        val contributesBindingAnnotation = classDescriptorForExclusion
          .annotationOrNull(contributesBindingFqName)
        val contributesMultibindingAnnotation = classDescriptorForExclusion
          .annotationOrNull(contributesMultibindingFqName)
        val contributesSubcomponentAnnotation = classDescriptorForExclusion
          .annotationOrNull(contributesSubcomponentFqName)

        // Verify that the replaced classes use the same scope.
        val scopeOfExclusion = contributesToAnnotation?.scope(module)
          ?: contributesBindingAnnotation?.scope(module)
          ?: contributesMultibindingAnnotation?.scope(module)
          ?: contributesSubcomponentAnnotation?.parentScope(module)
          ?: throw AnvilCompilationException(
            thisDescriptor,
            "Could not determine the scope of the excluded class " +
              "${classDescriptorForExclusion.fqNameSafe}."
          )

        if (scopeOfExclusion.fqNameSafe != scopeFqName) {
          throw AnvilCompilationException(
            thisDescriptor,
            "${thisDescriptor.fqNameSafe} with scope $scopeFqName wants to exclude " +
              "${classDescriptorForExclusion.fqNameSafe} with scope " +
              "${scopeOfExclusion.fqNameSafe}. The exclusion must use the same scope."
          )
        }

        argumentType.asmType(codegen.typeMapper)
      }
      ?: emptyList()

    val replacedModules = modules
      .filter { (classDescriptor, _) ->
        // Ignore replaced modules or bindings specified by excluded modules.
        classDescriptor.defaultType.asmType(codegen.typeMapper) !in excludedModules
      }
      .flatMap { (classDescriptor, contributeAnnotation) ->
        classDescriptor.toClassReference()
          .replaces(module, contributeAnnotation.requireFqName())
          .map { it.requireClassDescriptor(module) }
          .map { classDescriptorForReplacement ->
            // Verify has @Module annotation. It doesn't make sense for a Dagger module to
            // replace a non-Dagger module.
            if (classDescriptorForReplacement.annotationOrNull(daggerModuleFqName) == null &&
              classDescriptorForReplacement.annotationOrNull(contributesBindingFqName) == null &&
              classDescriptorForReplacement.annotationOrNull(contributesMultibindingFqName) == null
            ) {
              throw AnvilCompilationException(
                classDescriptor,
                "${classDescriptor.fqNameSafe} wants to replace " +
                  "${classDescriptorForReplacement.fqNameSafe}, but the class being " +
                  "replaced is not a Dagger module."
              )
            }

            checkSameScope(classDescriptor, classDescriptorForReplacement, module, scopeFqName)

            classDescriptorForReplacement.defaultType.asmType(codegen.typeMapper)
          }
      }

    fun replacedModulesByContributedBinding(
      annotationFqName: FqName,
      hintPackagePrefix: String
    ): Sequence<Type> {
      return classScanner
        .findContributedClasses(
          module = module,
          packageName = hintPackagePrefix,
          annotation = annotationFqName,
          scope = scopeFqName
        )
        .flatMap { contributedClass ->
          val annotation = contributedClass.annotation(annotationFqName)
          if (scopeFqName == annotation.scope(module).fqNameSafe) {
            contributedClass.toClassReference()
              .replaces(module, annotationFqName)
              .map { it.requireClassDescriptor(module) }
              .map { classDescriptorForReplacement ->
                checkSameScope(
                  contributedClass,
                  classDescriptorForReplacement,
                  module,
                  scopeFqName
                )
                classDescriptorForReplacement.defaultType.asmType(codegen.typeMapper)
              }
          } else {
            emptyList()
          }
        }
    }

    val replacedModulesByContributedBindings = replacedModulesByContributedBinding(
      annotationFqName = contributesBindingFqName,
      hintPackagePrefix = HINT_BINDING_PACKAGE_PREFIX
    )

    val replacedModulesByContributedMultibindings = replacedModulesByContributedBinding(
      annotationFqName = contributesMultibindingFqName,
      hintPackagePrefix = HINT_MULTIBINDING_PACKAGE_PREFIX
    )

    if (predefinedModules != null) {
      val intersect = predefinedModules.intersect(excludedModules.toSet())
      if (intersect.isNotEmpty()) {
        throw AnvilCompilationException(
          thisDescriptor,
          "${thisDescriptor.name} includes and excludes modules " +
            "at the same time: ${intersect.joinToString { it.className }}"
        )
      }
    }

    val contributedSubcomponentModules =
      findContributedSubcomponentModules(thisDescriptor, scopeFqName, module)
        .map { codegen.typeMapper.mapType(it) }

    val contributedModules = modules
      .asSequence()
      .map { codegen.typeMapper.mapType(it.first) }
      .minus(replacedModules.toSet())
      .minus(replacedModulesByContributedBindings.toSet())
      .minus(replacedModulesByContributedMultibindings.toSet())
      .minus(excludedModules.toSet())
      .plus(contributedSubcomponentModules)
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
    contributedClass: ClassDescriptor,
    classDescriptorForReplacement: ClassDescriptor,
    module: ModuleDescriptor,
    scopeFqName: FqName
  ) {
    val contributesToAnnotation = classDescriptorForReplacement
      .annotationOrNull(contributesToFqName)
    val contributesBindingAnnotation = classDescriptorForReplacement
      .annotationOrNull(contributesBindingFqName)
    val contributesMultibindingAnnotation = classDescriptorForReplacement
      .annotationOrNull(contributesMultibindingFqName)

    // Verify that the replaced classes use the same scope.
    val scopeOfReplacement = contributesToAnnotation?.scope(module)
      ?: contributesBindingAnnotation?.scope(module)
      ?: contributesMultibindingAnnotation?.scope(module)
      ?: throw AnvilCompilationException(
        contributedClass,
        "Could not determine the scope of the replaced class " +
          "${classDescriptorForReplacement.fqNameSafe}."
      )

    if (scopeOfReplacement.fqNameSafe != scopeFqName) {
      throw AnvilCompilationException(
        contributedClass,
        "${contributedClass.fqNameSafe} with scope $scopeFqName wants to replace " +
          "${classDescriptorForReplacement.fqNameSafe} with scope " +
          "${scopeOfReplacement.fqNameSafe}. The replacement must use the same scope."
      )
    }
  }

  private fun findContributedSubcomponentModules(
    descriptor: ClassDescriptor,
    scope: FqName,
    module: ModuleDescriptor
  ): Sequence<ClassDescriptor> {
    return classScanner
      .findContributedClasses(
        module = module,
        packageName = HINT_SUBCOMPONENTS_PACKAGE_PREFIX,
        annotation = contributesSubcomponentFqName,
        scope = null
      )
      .filter {
        it.annotation(contributesSubcomponentFqName).parentScope(module).fqNameSafe == scope
      }
      .mapNotNull { contributedSubcomponent ->
        contributedSubcomponent.requireClassId()
          .generatedAnvilSubcomponent(descriptor.requireClassId())
          .createNestedClassId(Name.identifier(SUBCOMPONENT_MODULE))
          .classDescriptorOrNull(module)
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
      descriptor.annotationOrNull(mergeComponentFqName)
        ?.let {
          return AnnotationHolder(
            it,
            MergeComponent::class,
            Component::class,
            daggerComponentFqName,
            "modules"
          )
        }
      descriptor.annotationOrNull(mergeSubcomponentFqName)
        ?.let {
          return AnnotationHolder(
            it,
            MergeSubcomponent::class,
            Subcomponent::class,
            daggerSubcomponentFqName,
            "modules"
          )
        }
      descriptor.annotationOrNull(mergeModulesFqName)
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
