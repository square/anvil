package com.squareup.anvil.compiler

import com.squareup.anvil.annotations.ContributesTo
import com.squareup.anvil.compiler.codegen.atLeastOneAnnotation
import com.squareup.anvil.compiler.codegen.checkClassIsPublic
import com.squareup.anvil.compiler.codegen.find
import com.squareup.anvil.compiler.codegen.findAll
import com.squareup.anvil.compiler.codegen.generatedAnvilSubcomponent
import com.squareup.anvil.compiler.codegen.parentScope
import com.squareup.anvil.compiler.codegen.reference.RealAnvilModuleDescriptor
import com.squareup.anvil.compiler.internal.argumentType
import com.squareup.anvil.compiler.internal.reference.AnnotationReference
import com.squareup.anvil.compiler.internal.reference.AnvilCompilationExceptionClassReference
import com.squareup.anvil.compiler.internal.reference.ClassReference
import com.squareup.anvil.compiler.internal.reference.argumentAt
import com.squareup.anvil.compiler.internal.reference.generateClassName
import com.squareup.anvil.compiler.internal.reference.toClassReference
import com.squareup.anvil.compiler.internal.safePackageString
import dagger.Component
import dagger.Module
import dagger.Subcomponent
import org.jetbrains.kotlin.codegen.ImplementationBodyCodegen
import org.jetbrains.kotlin.codegen.asmType
import org.jetbrains.kotlin.codegen.extensions.ExpressionCodegenExtension
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.constants.ArrayValue
import org.jetbrains.kotlin.resolve.constants.ConstantValue
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.org.objectweb.asm.AnnotationVisitor
import org.jetbrains.org.objectweb.asm.Type
import kotlin.reflect.KClass

/**
 * Finds all contributed Dagger modules and adds them to Dagger components annotated with
 * `@MergeComponent` or `@MergeSubcomponent`. This class is responsible for generating the
 * `@Component` and `@Subcomponent` annotation required for Dagger.
 */
internal class ModuleMerger(
  private val classScanner: ClassScanner,
  private val moduleDescriptorFactory: RealAnvilModuleDescriptor.Factory
) : ExpressionCodegenExtension {

  override fun generateClassSyntheticParts(codegen: ImplementationBodyCodegen) {
    if (codegen.descriptor.shouldIgnore()) return

    val module = moduleDescriptorFactory.create(codegen.descriptor.module)
    val clazz = codegen.descriptor.toClassReference(module)

    val annotations = clazz.annotations
      .findAll(mergeComponentFqName, mergeSubcomponentFqName, mergeModulesFqName)
      .ifEmpty { return }

    val daggerAnnotationClass = annotations[0].daggerAnnotationClass
    val daggerModulesKeyword = annotations[0].modulesKeyword
    val scopes = annotations.map { it.scope() }

    val predefinedModules = annotations.flatMap {
      it.argumentAt(it.modulesKeyword, index = 1)?.value<List<ClassReference>>().orEmpty()
    }
    val anvilModuleName = createAnvilModuleName(clazz)

    val contributesAnnotations = scopes
      .flatMap { scope ->
        classScanner
          .findContributedClasses(
            module = module,
            annotation = contributesToFqName,
            scope = scope.fqName
          )
      }
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
      .flatMap { contributedClass ->
        contributedClass.annotations
          .find(contributesToFqName)
          .filter { it.scope() in scopes }
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

    val excludedModules = annotations.flatMap { it.exclude() }
      .onEach { excludedClass ->
        // Verify that the excluded classes use the same scope.
        val contributesToOurScope = excludedClass.annotations
          .findAll(contributesToFqName, contributesBindingFqName, contributesMultibindingFqName)
          .map { it.scope() }
          .plus(
            excludedClass.annotations
              .find(contributesSubcomponentFqName)
              .map { it.parentScope() }
          )
          .any { scope -> scope in scopes }

        if (!contributesToOurScope) {
          throw AnvilCompilationExceptionClassReference(
            message = "${clazz.fqName} with scopes " +
              "${scopes.joinToString(prefix = "[", postfix = "]") { it.fqName.asString() }} " +
              "wants to exclude ${excludedClass.fqName}, but the excluded class isn't " +
              "contributed to the same scope.",
            classReference = clazz
          )
        }
      }

    val replacedModules = contributesAnnotations
      // Ignore replaced modules or bindings specified by excluded modules.
      .filter { contributesAnnotation ->
        contributesAnnotation.declaringClass() !in excludedModules
      }
      .flatMap { contributesAnnotation ->
        val contributedClass = contributesAnnotation.declaringClass()
        contributesAnnotation.replaces()
          .onEach { classToReplace ->
            // Verify has @Module annotation. It doesn't make sense for a Dagger module to
            // replace a non-Dagger module.
            if (!classToReplace.isAnnotatedWith(daggerModuleFqName) &&
              !classToReplace.isAnnotatedWith(contributesBindingFqName) &&
              !classToReplace.isAnnotatedWith(contributesMultibindingFqName)
            ) {
              throw AnvilCompilationExceptionClassReference(
                message = "${contributedClass.fqName} wants to replace " +
                  "${classToReplace.fqName}, but the class being replaced is not a Dagger module.",
                classReference = contributedClass
              )
            }

            checkSameScope(contributedClass, classToReplace, scopes)
          }
      }

    fun replacedModulesByContributedBinding(
      annotationFqName: FqName
    ): Sequence<ClassReference> {
      return scopes.asSequence()
        .flatMap { scope ->
          classScanner
            .findContributedClasses(
              module = module,
              annotation = annotationFqName,
              scope = scope.fqName
            )
        }
        .flatMap { contributedClass ->
          contributedClass.annotations
            .find(annotationName = annotationFqName)
            .filter { it.scope() in scopes }
            .flatMap { it.replaces() }
            .onEach { classToReplace ->
              checkSameScope(contributedClass, classToReplace, scopes)
            }
        }
    }

    val replacedModulesByContributedBindings = replacedModulesByContributedBinding(
      annotationFqName = contributesBindingFqName
    )

    val replacedModulesByContributedMultibindings = replacedModulesByContributedBinding(
      annotationFqName = contributesMultibindingFqName
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
      findContributedSubcomponentModules(clazz, scopes, module)

    val contributedModuleTypes = contributesAnnotations
      .asSequence()
      .map { it.declaringClass() }
      .minus(replacedModules.toSet())
      .minus(replacedModulesByContributedBindings.toSet())
      .minus(replacedModulesByContributedMultibindings.toSet())
      .minus(excludedModules.toSet())
      .plus(contributedSubcomponentModules)
      .toSet()
      .types(codegen)

    codegen.v
      .newAnnotation("L${daggerAnnotationClass.java.canonicalName.replace('.', '/')};", true)
      .use {
        visitArray(daggerModulesKeyword)
          .use {
            predefinedModules.types(codegen).forEach { visit(daggerModulesKeyword, it) }
            contributedModuleTypes.forEach { visit(daggerModulesKeyword, it) }
          }

        if (annotations[0].fqName == mergeComponentFqName) {
          copyArrayValue(codegen, module, annotations, "dependencies")
        }

        if (annotations[0].fqName == mergeModulesFqName) {
          copyArrayValue(codegen, module, annotations, "subcomponents")
        }
      }
  }

  @Suppress("UNCHECKED_CAST")
  private fun Collection<ClassReference>.types(codegen: ImplementationBodyCodegen): List<Type> {
    return (this as Collection<ClassReference.Descriptor>)
      .map { codegen.typeMapper.mapType(it.clazz) }
  }

  private fun AnnotationVisitor.copyArrayValue(
    codegen: ImplementationBodyCodegen,
    module: ModuleDescriptor,
    annotations: List<AnnotationReference.Descriptor>,
    name: String
  ) {
    val predefinedValues = annotations
      .flatMap { annotation ->
        (annotation.arguments.singleOrNull { it.name == name }?.argument as? ArrayValue)
          ?.value
          .orEmpty()
      }
      .map { it.toType(codegen, module) }
      .ifEmpty { null }

    visitArray(name).use {
      predefinedValues?.forEach { visit(name, it) }
    }
  }

  private fun ConstantValue<*>.toType(
    codegen: ImplementationBodyCodegen,
    module: ModuleDescriptor
  ): Type = argumentType(module).asmType(codegen.typeMapper)

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
    scopes: List<ClassReference>
  ) {
    val contributesToOurScope = classToReplace.annotations
      .findAll(contributesToFqName, contributesBindingFqName, contributesMultibindingFqName)
      .map { it.scope() }
      .any { scope -> scope in scopes }

    if (!contributesToOurScope) {
      throw AnvilCompilationExceptionClassReference(
        classReference = contributedClass,
        message = "${contributedClass.fqName} with scopes " +
          "${scopes.joinToString(prefix = "[", postfix = "]") { it.fqName.asString() }} " +
          "wants to replace ${classToReplace.fqName}, but the replaced class isn't " +
          "contributed to the same scope."
      )
    }
  }

  private fun findContributedSubcomponentModules(
    clazz: ClassReference.Descriptor,
    scopes: List<ClassReference>,
    module: ModuleDescriptor
  ): Sequence<ClassReference> {
    return classScanner
      .findContributedClasses(
        module = module,
        annotation = contributesSubcomponentFqName,
        scope = null
      )
      .filter { contributedClass ->
        contributedClass
          .atLeastOneAnnotation(contributesSubcomponentFqName)
          .any { it.parentScope() in scopes }
      }
      .mapNotNull { contributedSubcomponent ->
        contributedSubcomponent.classId
          .generatedAnvilSubcomponent(clazz.classId)
          .createNestedClassId(Name.identifier(SUBCOMPONENT_MODULE))
          .classReferenceOrNull(module)
      }
  }
}

private val AnnotationReference.daggerAnnotationClass: KClass<*>
  get() = when (fqName) {
    mergeComponentFqName -> Component::class
    mergeSubcomponentFqName -> Subcomponent::class
    mergeModulesFqName -> Module::class
    else -> throw NotImplementedError("Don't know how to handle $this.")
  }

private val AnnotationReference.modulesKeyword: String
  get() = when (fqName) {
    mergeComponentFqName, mergeSubcomponentFqName -> "modules"
    mergeModulesFqName -> "includes"
    else -> throw NotImplementedError("Don't know how to handle $this.")
  }
