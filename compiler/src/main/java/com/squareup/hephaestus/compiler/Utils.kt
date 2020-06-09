package com.squareup.hephaestus.compiler

import com.squareup.hephaestus.annotations.ContributesTo
import com.squareup.hephaestus.annotations.MergeComponent
import com.squareup.hephaestus.annotations.MergeSubcomponent
import com.squareup.hephaestus.annotations.compat.MergeInterfaces
import com.squareup.hephaestus.annotations.compat.MergeModules
import dagger.Component
import dagger.Module
import dagger.Subcomponent
import org.jetbrains.kotlin.codegen.asmType
import org.jetbrains.kotlin.codegen.state.KotlinTypeMapper
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.has
import org.jetbrains.kotlin.platform.jvm.JvmPlatform
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.constants.ConstantValue
import org.jetbrains.kotlin.resolve.constants.KClassValue
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.org.objectweb.asm.Type
import javax.annotation.Generated

internal val mergeComponentFqName = FqName(MergeComponent::class.java.canonicalName)
internal val mergeSubcomponentFqName = FqName(MergeSubcomponent::class.java.canonicalName)
internal val mergeInterfacesFqName = FqName(MergeInterfaces::class.java.canonicalName)
internal val mergeModulesFqName = FqName(MergeModules::class.java.canonicalName)
internal val contributesToFqName = FqName(ContributesTo::class.java.canonicalName)
internal val daggerComponentFqName = FqName(Component::class.java.canonicalName)
internal val daggerSubcomponentFqName = FqName(Subcomponent::class.java.canonicalName)
internal val daggerModuleFqName = FqName(Module::class.java.canonicalName)
private val generatedFqName = FqName(Generated::class.java.canonicalName)
private val suppressWarningsFqName = FqName(SuppressWarnings::class.java.canonicalName)

internal const val HINT_PACKAGE_PREFIX = "hint.hephaestus"

internal fun ClassDescriptor.findAnnotation(
  annotationFqName: FqName,
  scope: ClassDescriptor? = null
): AnnotationDescriptor? {
  // Must be JVM, we don't support anything else.
  if (!module.platform.has<JvmPlatform>()) return null
  val annotationDescriptor = annotations.findAnnotation(annotationFqName)
  if (scope == null || annotationDescriptor == null) return annotationDescriptor

  val foundTarget = annotationDescriptor.scope(scope.module)
  return if (scope.fqNameSafe == foundTarget.fqNameSafe) annotationDescriptor else null
}

internal fun ConstantValue<*>.toType(
  module: ModuleDescriptor,
  typeMapper: KotlinTypeMapper
): Type {
  // This is a Kotlin class with the actual type as argument: KClass<OurType>
  val kClassType = getType(module)
  return kClassType.argumentType()
      .asmType(typeMapper)
}

// When the Kotlin type is of the form: KClass<OurType>.
internal fun KotlinType.argumentType(): KotlinType = arguments.first().type

internal fun AnnotationDescriptor.getAnnotationValue(key: String): ConstantValue<*>? =
  allValueArguments[Name.identifier(key)]

internal fun AnnotationDescriptor.scope(module: ModuleDescriptor): ClassDescriptor {
  val kClassValue = requireNotNull(getAnnotationValue("scope")) as KClassValue
  return DescriptorUtils.getClassDescriptorForType(kClassValue.getType(module).argumentType())
}

internal fun ClassDescriptor.isGeneratedDaggerComponent(): Boolean {
  return fqNameSafe.shortName().asString().startsWith("Dagger") &&
      (findAnnotation(generatedFqName) != null || findAnnotation(suppressWarningsFqName) != null)
}
