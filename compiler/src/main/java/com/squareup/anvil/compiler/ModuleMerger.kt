package com.squareup.anvil.compiler

import com.squareup.anvil.annotations.ContributesTo
import com.squareup.anvil.annotations.MergeComponent
import com.squareup.anvil.annotations.MergeSubcomponent
import com.squareup.anvil.annotations.compat.MergeModules
import dagger.Component
import dagger.Module
import dagger.Subcomponent
import org.jetbrains.kotlin.codegen.ImplementationBodyCodegen
import org.jetbrains.kotlin.codegen.extensions.ExpressionCodegenExtension
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.EffectiveVisibility.Public
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.descriptors.effectiveVisibility
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.constants.ArrayValue
import org.jetbrains.kotlin.resolve.constants.ConstantValue
import org.jetbrains.kotlin.resolve.constants.KClassValue
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
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
  private val classScanner: ClassScanner
) : ExpressionCodegenExtension {

  override fun generateClassSyntheticParts(codegen: ImplementationBodyCodegen) {
    val holder = AnnotationHolder.create(codegen.descriptor) ?: return
    val (mergeAnnotation, annotationClass, daggerClass, daggerFqName, modulesKeyword) = holder

    if (codegen.descriptor.annotationOrNull(daggerFqName) != null) {
      throw AnvilCompilationException(
          codegen.descriptor,
          "When using @${annotationClass.simpleName} it's not allowed to annotate the same " +
              "class with @${daggerClass.simpleName}. The Dagger annotation will be generated."
      )
    }

    val scope = mergeAnnotation.scope(codegen.descriptor.module)

    val predefinedModules =
      (mergeAnnotation.getAnnotationValue(modulesKeyword) as? ArrayValue)
          ?.value
          ?.map { it.toType(codegen) }

    val modules = classScanner
        .findContributedClasses(codegen.descriptor.module)
        .asSequence()
        .mapNotNull {
          val contributesAnnotation =
            it.annotationOrNull(contributesToFqName, scope = scope) ?: return@mapNotNull null
          it to contributesAnnotation
        }
        .filter { (classDescriptor, _) ->
          val moduleAnnotation = classDescriptor.annotationOrNull(daggerModuleFqName)
          if (!DescriptorUtils.isInterface(classDescriptor) && moduleAnnotation == null) {
            throw AnvilCompilationException(
                classDescriptor,
                "${classDescriptor.fqNameSafe} is annotated with " +
                    "@${ContributesTo::class.simpleName}, but this class is neither an interface " +
                    "nor a Dagger module. Did you forget to add @${Module::class.simpleName}?"
            )
          }

          moduleAnnotation != null
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

    val replacedModules = modules
        .mapNotNull { (classDescriptor, contributeAnnotation) ->
          val kClassValue = contributeAnnotation.getAnnotationValue("replaces")
              as? KClassValue ?: return@mapNotNull null

          // Verify has @Module annotation. It doesn't make sense for a Dagger module to replace a
          // non-Dagger module.
          val kotlinType = kClassValue.getType(codegen.descriptor.module)
              .argumentType()
          val classDescriptorForReplacement = DescriptorUtils.getClassDescriptorForType(kotlinType)
          if (classDescriptorForReplacement.annotationOrNull(daggerModuleFqName) == null) {
            throw AnvilCompilationException(
                classDescriptor,
                "${classDescriptor.fqNameSafe} wants to replace " +
                    "${classDescriptorForReplacement.fqNameSafe}, but the class being replaced " +
                    "is not a Dagger module."
            )
          }

          kClassValue.toType(codegen)
        }

    val excludedModules = (mergeAnnotation.getAnnotationValue("exclude") as? ArrayValue)
        ?.value
        ?.map { it.toType(codegen) }
        ?: emptyList()

    if (predefinedModules != null) {
      val intersect = predefinedModules.intersect(excludedModules)
      if (intersect.isNotEmpty()) {
        throw AnvilCompilationException(
            codegen.descriptor, "${codegen.descriptor.name} includes and excludes modules " +
            "at the same time: ${intersect.joinToString { it.className }}"
        )
      }
    }

    val contributedModules = modules
        .map { it.first }
        .map { codegen.typeMapper.mapType(it) }
        .minus(replacedModules)
        .minus(excludedModules)
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
                it, MergeComponent::class, Component::class,
                daggerComponentFqName, "modules"
            )
          }
      descriptor.annotationOrNull(mergeSubcomponentFqName)
          ?.let {
            return AnnotationHolder(
                it, MergeSubcomponent::class, Subcomponent::class,
                daggerSubcomponentFqName, "modules"
            )
          }
      descriptor.annotationOrNull(mergeModulesFqName)
          ?.let {
            return AnnotationHolder(
                it, MergeModules::class, Module::class,
                daggerModuleFqName, "includes"
            )
          }
      return null
    }
  }
}
