package com.squareup.anvil.compiler

import com.squareup.anvil.annotations.ContributesTo
import com.squareup.anvil.annotations.MergeComponent
import com.squareup.anvil.annotations.MergeSubcomponent
import com.squareup.anvil.annotations.compat.MergeModules
import com.squareup.anvil.compiler.codegen.atLeastOneAnnotation
import com.squareup.anvil.compiler.codegen.checkClassIsPublic
import com.squareup.anvil.compiler.codegen.find
import com.squareup.anvil.compiler.codegen.generatedAnvilSubcomponent
import com.squareup.anvil.compiler.codegen.modules
import com.squareup.anvil.compiler.codegen.parentScope
import com.squareup.anvil.compiler.codegen.reference.RealAnvilModuleDescriptor
import com.squareup.anvil.compiler.internal.classDescriptorOrNull
import com.squareup.anvil.compiler.internal.getAnnotationValue
import com.squareup.anvil.compiler.internal.reference.AnnotationReference
import com.squareup.anvil.compiler.internal.reference.AnvilCompilationExceptionClassReference
import com.squareup.anvil.compiler.internal.reference.ClassReference
import com.squareup.anvil.compiler.internal.reference.generateClassName
import com.squareup.anvil.compiler.internal.reference.isAnnotatedWith
import com.squareup.anvil.compiler.internal.reference.toClassReference
import com.squareup.anvil.compiler.internal.safePackageString
import com.squareup.anvil.compiler.internal.toType
import dagger.Component
import dagger.Module
import dagger.Subcomponent
import org.jetbrains.kotlin.codegen.ImplementationBodyCodegen
import org.jetbrains.kotlin.codegen.extensions.ExpressionCodegenExtension
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.constants.ArrayValue
import org.jetbrains.kotlin.resolve.constants.ConstantValue
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.utils.addToStdlib.cast
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
    val module = RealAnvilModuleDescriptor(codegen.descriptor.module)
    val clazz = codegen.descriptor.toClassReference(module)
    val holder = AnnotationHolder.create(clazz) ?: return
    val (mergeAnnotation, annotationClass, daggerClass, daggerFqName, modulesKeyword) = holder

    if (clazz.isAnnotatedWith(daggerFqName)) {
      throw AnvilCompilationExceptionClassReference(
        message = "When using @${annotationClass.simpleName} it's not allowed to annotate the " +
          "same class with @${daggerClass.simpleName}. The Dagger annotation will be generated.",
        classReference = clazz
      )
    }

    val mergeScope = mergeAnnotation.scope()
    val predefinedModules = mergeAnnotation.modules()
    val anvilModuleName = createAnvilModuleName(clazz)

    val modules = classScanner
      .findContributedClasses(
        module = module,
        packageName = HINT_CONTRIBUTES_PACKAGE_PREFIX,
        annotation = contributesToFqName,
        scope = mergeScope.fqName
      )
      .map { it.toClassReference(module) }
      .filter {
        // We generate a Dagger module for each merged component. We use Anvil itself to
        // contribute this generated module. It's possible that there are multiple components
        // merging the same scope or the same scope is merged in different Gradle modules which
        // depend on each other. This would cause duplicate bindings, because the generated
        // modules contain the same bindings and are contributed to the same scope. To avoid this
        // issue we filter all generated Anvil modules except for the one that was generated for
        // this specific class.
        !it.fqName.isAnvilModule() || it.fqName == anvilModuleName
      }
      .mapNotNull { contributedClass ->
        contributedClass.annotations.find(
          annotationName = contributesToFqName,
          scopeName = mergeScope.fqName
        ).singleOrNull()
      }
      .filter { contributesAnnotation ->
        val contributedClass = contributesAnnotation.declaringClass()
        val moduleAnnotation = contributedClass.annotations.find(daggerModuleFqName).singleOrNull()
        val mergeModulesAnnotation =
          contributedClass.annotations.find(mergeModulesFqName).singleOrNull()

        if (!contributedClass.isInterface() &&
          moduleAnnotation == null &&
          mergeModulesAnnotation == null
        ) {
          throw AnvilCompilationExceptionClassReference(
            contributedClass,
            "${contributedClass.fqName} is annotated with " +
              "@${ContributesTo::class.simpleName}, but this class is neither an interface " +
              "nor a Dagger module. Did you forget to add @${Module::class.simpleName}?"
          )
        }

        contributedClass.checkClassIsPublic {
          "${contributedClass.fqName} is contributed to the Dagger graph, but the " +
            "module is not public. Only public modules are supported."
        }

        moduleAnnotation != null || mergeModulesAnnotation != null
      }
      // Convert the sequence to a list to avoid iterating it twice. We use the result twice
      // for replaced classes and the final result.
      .toList()

    val excludedModules = mergeAnnotation.exclude()
      .onEach { excludedClass ->
        val contributesToAnnotation =
          excludedClass.annotations.find(contributesToFqName).singleOrNull()
        val contributesBindingAnnotation =
          excludedClass.annotations.find(contributesBindingFqName).singleOrNull()
        val contributesMultibindingAnnotation =
          excludedClass.annotations.find(contributesMultibindingFqName).singleOrNull()
        val contributesSubcomponentAnnotation =
          excludedClass.annotations.find(contributesSubcomponentFqName).singleOrNull()

        // Verify that the replaced classes use the same scope.
        val scopeOfExclusion = contributesToAnnotation?.scopeOrNull()
          ?: contributesBindingAnnotation?.scopeOrNull()
          ?: contributesMultibindingAnnotation?.scopeOrNull()
          ?: contributesSubcomponentAnnotation?.parentScope()
          ?: throw AnvilCompilationExceptionClassReference(
            message = "Could not determine the scope of the excluded class " +
              "${excludedClass.fqName}.",
            classReference = excludedClass
          )

        if (scopeOfExclusion != mergeScope) {
          throw AnvilCompilationExceptionClassReference(
            classReference = clazz,
            message = "${clazz.fqName} with scope ${mergeScope.fqName} wants to exclude " +
              "${excludedClass.fqName} with scope " +
              "${scopeOfExclusion.fqName}. The exclusion must use the same scope."
          )
        }
      }

    val replacedModules = modules
      .filter { contributesAnnotation ->
        // Ignore replaced modules or bindings specified by excluded modules.
        contributesAnnotation.declaringClass() !in excludedModules
      }
      .flatMap { contributesAnnotation ->
        val contributedClass = contributesAnnotation.declaringClass()
        contributedClass
          .atLeastOneAnnotation(contributesAnnotation.fqName).single()
          .replaces()
          .onEach { classToReplace ->
            // Verify has @Module annotation. It doesn't make sense for a Dagger module to
            // replace a non-Dagger module.
            if (classToReplace.annotations.find(daggerModuleFqName).singleOrNull() == null &&
              classToReplace.annotations.find(contributesBindingFqName).singleOrNull() == null &&
              classToReplace.annotations.find(contributesMultibindingFqName).singleOrNull() == null
            ) {
              throw AnvilCompilationExceptionClassReference(
                contributedClass,
                "${contributedClass.fqName} wants to replace " +
                  "${classToReplace.fqName}, but the class being " +
                  "replaced is not a Dagger module."
              )
            }

            checkSameScope(contributedClass, classToReplace, mergeScope)
          }
      }

    fun replacedModulesByContributedBinding(
      annotationFqName: FqName,
      hintPackagePrefix: String
    ): Sequence<ClassReference> {
      return classScanner
        .findContributedClasses(
          module = module,
          packageName = hintPackagePrefix,
          annotation = annotationFqName,
          scope = mergeScope.fqName
        )
        .map { it.toClassReference(module) }
        .flatMap { contributedClass ->
          val annotation = contributedClass.atLeastOneAnnotation(annotationFqName).single()
          if (mergeScope == annotation.scope()) {
            contributedClass
              .atLeastOneAnnotation(annotationFqName).single()
              .replaces()
              .onEach { classToReplace ->
                checkSameScope(contributedClass, classToReplace, mergeScope)
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

    val intersect = predefinedModules.intersect(excludedModules.toSet())
    if (intersect.isNotEmpty()) {
      throw AnvilCompilationExceptionClassReference(
        clazz,
        "${clazz.clazz.name} includes and excludes modules " +
          "at the same time: ${intersect.joinToString { it.classId.relativeClassName.toString() }}"
      )
    }

    val contributedSubcomponentModules =
      findContributedSubcomponentModules(clazz, mergeScope, module)

    val contributedModuleTypes = modules
      .asSequence()
      .map { it.declaringClass() }
      .minus(replacedModules)
      .minus(replacedModulesByContributedBindings)
      .minus(replacedModulesByContributedMultibindings)
      .minus(excludedModules)
      .plus(contributedSubcomponentModules)
      .toSet()
      .types(codegen)

    codegen.v
      .newAnnotation("L${daggerClass.java.canonicalName.replace('.', '/')};", true)
      .use {
        visitArray(modulesKeyword)
          .use {
            predefinedModules.toSet().types(codegen).forEach { visit(modulesKeyword, it) }
            contributedModuleTypes.forEach { visit(modulesKeyword, it) }
          }

        if (holder.isComponent) {
          copyArrayValue(codegen, mergeAnnotation, "dependencies")
        }

        if (holder.isModule) {
          copyArrayValue(codegen, mergeAnnotation, "subcomponents")
        }
      }
  }

  private fun Set<ClassReference>.types(codegen: ImplementationBodyCodegen): Set<Type> {
    return cast<Set<ClassReference.Descriptor>>()
      .map { codegen.typeMapper.mapType(it.clazz) }
      .toSet()
  }

  private fun AnnotationVisitor.copyArrayValue(
    codegen: ImplementationBodyCodegen,
    annotation: AnnotationReference.Descriptor,
    name: String
  ) {
    val predefinedValues =
      (annotation.annotation.getAnnotationValue(name) as? ArrayValue)
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

  private fun createAnvilModuleName(clazz: ClassReference): FqName {
    val name = "$MODULE_PACKAGE_PREFIX." +
      clazz.packageFqName.safePackageString() +
      clazz.generateClassName(
        separator = "",
        suffix = ANVIL_MODULE_SUFFIX
      ).relativeClassName.toString()
    return FqName(name)
  }

  private fun checkSameScope(
    contributedClass: ClassReference,
    classToReplace: ClassReference,
    mergeScope: ClassReference
  ) {
    val contributesToAnnotation =
      classToReplace.annotations.find(contributesToFqName).singleOrNull()
    val contributesBindingAnnotation =
      classToReplace.annotations.find(contributesBindingFqName).singleOrNull()
    val contributesMultibindingAnnotation =
      classToReplace.annotations.find(contributesMultibindingFqName).singleOrNull()

    // Verify that the replaced classes use the same scope.
    val scopeOfReplacement = contributesToAnnotation?.scope()
      ?: contributesBindingAnnotation?.scope()
      ?: contributesMultibindingAnnotation?.scope()
      ?: throw AnvilCompilationExceptionClassReference(
        contributedClass,
        "Could not determine the scope of the replaced class ${classToReplace.fqName}."
      )

    if (scopeOfReplacement != mergeScope) {
      throw AnvilCompilationExceptionClassReference(
        contributedClass,
        "${contributedClass.fqName} with scope ${mergeScope.fqName} wants to replace " +
          "${classToReplace.fqName} with scope " +
          "${scopeOfReplacement.fqName}. The replacement must use the same scope."
      )
    }
  }

  private fun findContributedSubcomponentModules(
    clazz: ClassReference.Descriptor,
    scope: ClassReference,
    module: ModuleDescriptor
  ): Sequence<ClassReference> {
    return classScanner
      .findContributedClasses(
        module = module,
        packageName = HINT_SUBCOMPONENTS_PACKAGE_PREFIX,
        annotation = contributesSubcomponentFqName,
        scope = null
      )
      .map { it.toClassReference(module) }
      .filter {
        it.atLeastOneAnnotation(contributesSubcomponentFqName)
          .single()
          .parentScope() == scope
      }
      .mapNotNull { contributedSubcomponent ->
        contributedSubcomponent.classId
          .generatedAnvilSubcomponent(clazz.classId)
          .createNestedClassId(Name.identifier(SUBCOMPONENT_MODULE))
          .classDescriptorOrNull(module)
          ?.toClassReference(module)
      }
  }
}

@Suppress("DataClassPrivateConstructor")
private data class AnnotationHolder private constructor(
  val annotation: AnnotationReference.Descriptor,
  val annotationClass: KClass<*>,
  val daggerClass: KClass<*>,
  val daggerFqName: FqName,
  val modulesKeyword: String
) {
  val isComponent = annotationClass == MergeComponent::class
  val isModule = annotationClass == MergeModules::class

  companion object {
    internal fun create(clazz: ClassReference): AnnotationHolder? {
      clazz.annotations.find(mergeComponentFqName).singleOrNull()
        ?.let {
          return AnnotationHolder(
            it as AnnotationReference.Descriptor,
            MergeComponent::class,
            Component::class,
            daggerComponentFqName,
            "modules"
          )
        }
      clazz.annotations.find(mergeSubcomponentFqName).singleOrNull()
        ?.let {
          return AnnotationHolder(
            it as AnnotationReference.Descriptor,
            MergeSubcomponent::class,
            Subcomponent::class,
            daggerSubcomponentFqName,
            "modules"
          )
        }
      clazz.annotations.find(mergeModulesFqName).singleOrNull()
        ?.let {
          return AnnotationHolder(
            it as AnnotationReference.Descriptor,
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
