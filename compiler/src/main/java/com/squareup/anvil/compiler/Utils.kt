package com.squareup.anvil.compiler

import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.anvil.annotations.MergeComponent
import com.squareup.anvil.annotations.MergeSubcomponent
import com.squareup.anvil.annotations.compat.MergeInterfaces
import com.squareup.anvil.annotations.compat.MergeModules
import dagger.Binds
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
import org.jetbrains.kotlin.resolve.descriptorUtil.getSuperClassNotAny
import org.jetbrains.kotlin.resolve.descriptorUtil.getSuperInterfaces
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.org.objectweb.asm.Type

internal val mergeComponentFqName = FqName(MergeComponent::class.java.canonicalName)
internal val mergeSubcomponentFqName = FqName(MergeSubcomponent::class.java.canonicalName)
internal val mergeInterfacesFqName = FqName(MergeInterfaces::class.java.canonicalName)
internal val mergeModulesFqName = FqName(MergeModules::class.java.canonicalName)
internal val contributesToFqName = FqName(ContributesTo::class.java.canonicalName)
internal val contributesBindingFqName = FqName(ContributesBinding::class.java.canonicalName)
internal val daggerComponentFqName = FqName(Component::class.java.canonicalName)
internal val daggerSubcomponentFqName = FqName(Subcomponent::class.java.canonicalName)
internal val daggerModuleFqName = FqName(Module::class.java.canonicalName)
internal val daggerBindsFqName = FqName(Binds::class.java.canonicalName)

internal const val HINT_CONTRIBUTES_PACKAGE_PREFIX = "hint.anvil"
internal const val HINT_BINDING_PACKAGE_PREFIX = "anvil.hint.binding"
internal const val MODULE_PACKAGE_PREFIX = "anvil.module"

internal fun ClassDescriptor.annotationOrNull(
  annotationFqName: FqName,
  scope: ClassDescriptor? = null
): AnnotationDescriptor? {
  // Must be JVM, we don't support anything else.
  if (!module.platform.has<JvmPlatform>()) return null
  val annotationDescriptor = try {
    annotations.findAnnotation(annotationFqName)
  } catch (e: IllegalStateException) {
    // In some scenarios this exception is thrown. Throw a new exception with a better explanation.
    // Caused by: java.lang.IllegalStateException: Should not be called!
    // at org.jetbrains.kotlin.types.ErrorUtils$1.getPackage(ErrorUtils.java:95)
    throw AnvilCompilationException(
        this,
        message = "It seems like you tried to contribute an inner class to its outer class. This " +
            "is not supported and results in a compiler error.",
        cause = e
    )
  }
  if (scope == null || annotationDescriptor == null) return annotationDescriptor

  val foundTarget = annotationDescriptor.scope(scope.module)
  return if (scope.fqNameSafe == foundTarget.fqNameSafe) annotationDescriptor else null
}

internal fun ClassDescriptor.annotation(
  annotationFqName: FqName,
  scope: ClassDescriptor? = null
): AnnotationDescriptor = requireNotNull(annotationOrNull(annotationFqName, scope)) {
  "Couldn't find $annotationFqName with scope ${scope?.fqNameSafe} for $fqNameSafe."
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
  return DescriptorUtils.getClassDescriptorForType(
      kClassValue.getType(module)
          .argumentType()
  )
}

internal fun AnnotationDescriptor.boundType(
  module: ModuleDescriptor,
  annotatedClass: ClassDescriptor
): ClassDescriptor {
  (getAnnotationValue("boundType") as? KClassValue)
      ?.getType(module)
      ?.argumentType()
      ?.let { return DescriptorUtils.getClassDescriptorForType(it) }

  val directSuperTypes = annotatedClass.getSuperInterfaces() +
      (annotatedClass.getSuperClassNotAny()
          ?.let { listOf(it) }
          ?: emptyList())

  return directSuperTypes.singleOrNull()
      ?: throw AnvilCompilationException(
          classDescriptor = annotatedClass,
          message = "${annotatedClass.fqNameSafe} contributes a binding, but does not " +
              "specify the bound type. This is only allowed with one direct super type. If " +
              "there are multiple, then the bound type must be explicitly defined in the " +
              "@${contributesBindingFqName.shortName()} annotation."
      )
}
