package com.squareup.anvil.compiler

import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.anvil.annotations.MergeComponent
import com.squareup.anvil.annotations.MergeSubcomponent
import com.squareup.anvil.annotations.compat.MergeInterfaces
import com.squareup.anvil.annotations.compat.MergeModules
import com.squareup.anvil.compiler.codegen.requireFqName
import dagger.Binds
import dagger.Component
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.Subcomponent
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.internal.DoubleCheck
import org.jetbrains.kotlin.codegen.asmType
import org.jetbrains.kotlin.codegen.state.KotlinTypeMapper
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.descriptors.findClassAcrossModuleDependencies
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.has
import org.jetbrains.kotlin.platform.jvm.JvmPlatform
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.constants.ArrayValue
import org.jetbrains.kotlin.resolve.constants.BooleanValue
import org.jetbrains.kotlin.resolve.constants.ConstantValue
import org.jetbrains.kotlin.resolve.constants.KClassValue
import org.jetbrains.kotlin.resolve.constants.KClassValue.Value.NormalClass
import org.jetbrains.kotlin.resolve.descriptorUtil.annotationClass
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.descriptorUtil.getSuperClassNotAny
import org.jetbrains.kotlin.resolve.descriptorUtil.getSuperInterfaces
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.types.ErrorType
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.supertypes
import org.jetbrains.org.objectweb.asm.Type
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Qualifier

internal val mergeComponentFqName = FqName(MergeComponent::class.java.canonicalName)
internal val mergeSubcomponentFqName = FqName(MergeSubcomponent::class.java.canonicalName)
internal val mergeInterfacesFqName = FqName(MergeInterfaces::class.java.canonicalName)
internal val mergeModulesFqName = FqName(MergeModules::class.java.canonicalName)
internal val contributesToFqName = FqName(ContributesTo::class.java.canonicalName)
internal val contributesBindingFqName = FqName(ContributesBinding::class.java.canonicalName)
internal val contributesMultibindingFqName =
  FqName(ContributesMultibinding::class.java.canonicalName)
internal val daggerComponentFqName = FqName(Component::class.java.canonicalName)
internal val daggerSubcomponentFqName = FqName(Subcomponent::class.java.canonicalName)
internal val daggerModuleFqName = FqName(Module::class.java.canonicalName)
internal val daggerBindsFqName = FqName(Binds::class.java.canonicalName)
internal val daggerProvidesFqName = FqName(Provides::class.java.canonicalName)
internal val daggerLazyFqName = FqName(Lazy::class.java.canonicalName)
internal val injectFqName = FqName(Inject::class.java.canonicalName)
internal val qualifierFqName = FqName(Qualifier::class.java.canonicalName)
internal val assistedFqName = FqName(Assisted::class.java.canonicalName)
internal val assistedFactoryFqName = FqName(AssistedFactory::class.java.canonicalName)
internal val assistedInjectFqName = FqName(AssistedInject::class.java.canonicalName)
internal val providerFqName = FqName(Provider::class.java.canonicalName)
internal val jvmSuppressWildcardsFqName = FqName(JvmSuppressWildcards::class.java.canonicalName)
internal val publishedApiFqName = FqName(PublishedApi::class.java.canonicalName)

internal val daggerDoubleCheckFqNameString = DoubleCheck::class.java.canonicalName

internal const val HINT_CONTRIBUTES_PACKAGE_PREFIX = "anvil.hint.merge"
internal const val HINT_BINDING_PACKAGE_PREFIX = "anvil.hint.binding"
internal const val HINT_MULTIBINDING_PACKAGE_PREFIX = "anvil.hint.multibinding"
internal const val MODULE_PACKAGE_PREFIX = "anvil.module"

internal const val ANVIL_MODULE_SUFFIX = "AnvilModule"

internal const val REFERENCE_SUFFIX = "_reference"
internal const val SCOPE_SUFFIX = "_scope"
internal val propertySuffixes = arrayOf(REFERENCE_SUFFIX, SCOPE_SUFFIX)

internal fun ClassDescriptor.annotationOrNull(
  annotationFqName: FqName,
  scope: FqName? = null
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
  return if (scope == null || annotationDescriptor == null) {
    annotationDescriptor
  } else {
    annotationDescriptor.takeIf { scope == it.scope(module).fqNameSafe }
  }
}

internal fun ClassDescriptor.annotation(
  annotationFqName: FqName,
  scope: FqName? = null
): AnnotationDescriptor = requireNotNull(annotationOrNull(annotationFqName, scope)) {
  "Couldn't find $annotationFqName with scope $scope for $fqNameSafe."
}

internal fun ConstantValue<*>.toType(
  module: ModuleDescriptor,
  typeMapper: KotlinTypeMapper
): Type {
  // This is a Kotlin class with the actual type as argument: KClass<OurType>
  return argumentType(module).asmType(typeMapper)
}

// When the Kotlin type is of the form: KClass<OurType>.
internal fun KotlinType.argumentType(): KotlinType = arguments.first().type

internal fun KotlinType.classDescriptorForType() = DescriptorUtils.getClassDescriptorForType(this)

internal fun FqName.isAnvilModule(): Boolean {
  val name = asString()
  return name.startsWith(MODULE_PACKAGE_PREFIX) && name.endsWith(ANVIL_MODULE_SUFFIX)
}

internal fun AnnotationDescriptor.getAnnotationValue(key: String): ConstantValue<*>? =
  allValueArguments[Name.identifier(key)]

internal fun AnnotationDescriptor.scope(module: ModuleDescriptor): ClassDescriptor {
  val kClassValue = requireNotNull(getAnnotationValue("scope")) as KClassValue
  return kClassValue.argumentType(module).classDescriptorForType()
}

internal fun AnnotationDescriptor.replaces(module: ModuleDescriptor): List<ClassDescriptor> {
  return (getAnnotationValue("replaces") as? ArrayValue)
    ?.value
    ?.map {
      it.argumentType(module).classDescriptorForType()
    }
    ?: emptyList()
}

internal fun ConstantValue<*>.argumentType(module: ModuleDescriptor): KotlinType {
  val argumentType = getType(module).argumentType()
  if (argumentType !is ErrorType) return argumentType

  // Handle inner classes explicitly. When resolving the Kotlin type of inner class from
  // dependencies the compiler might fail. It tries to load my.package.Class$Inner and fails
  // whereas is should load my.package.Class.Inner.
  val normalClass = this.value
  if (normalClass !is NormalClass) return argumentType

  val classId = normalClass.value.classId

  return module
    .findClassAcrossModuleDependencies(
      classId = ClassId(
        classId.packageFqName,
        FqName(classId.relativeClassName.asString().replace('$', '.')),
        false
      )
    )
    ?.defaultType
    ?: throw AnvilCompilationException(
      "Couldn't resolve class across module dependencies for class ID: $classId"
    )
}

internal fun AnnotationDescriptor.boundType(
  module: ModuleDescriptor,
  annotatedClass: ClassDescriptor,
  isMultibinding: Boolean
): ClassDescriptor {
  (getAnnotationValue("boundType") as? KClassValue)
    ?.argumentType(module)
    ?.classDescriptorForType()
    ?.let { return it }

  val directSuperTypes = annotatedClass.getSuperInterfaces()
    .plus(
      annotatedClass.getSuperClassNotAny()
        ?.let { listOf(it) }
        ?: emptyList()
    )

  val boundType = directSuperTypes.singleOrNull()
  if (boundType != null) return boundType

  val annotationFqName =
    if (isMultibinding) contributesMultibindingFqName else contributesBindingFqName

  throw AnvilCompilationException(
    classDescriptor = annotatedClass,
    message = "${annotatedClass.fqNameSafe} contributes a binding, but does not " +
      "specify the bound type. This is only allowed with exactly one direct super type. " +
      "If there are multiple or none, then the bound type must be explicitly defined in " +
      "the @${annotationFqName.shortName()} annotation."
  )
}

internal fun AnnotationDescriptor.ignoreQualifier(): Boolean {
  return (getAnnotationValue("ignoreQualifier") as? BooleanValue)?.value ?: false
}

internal fun KtClassOrObject.generateClassName(
  separator: String = "_"
): String =
  parentsWithSelf
    .filterIsInstance<KtClassOrObject>()
    .toList()
    .reversed()
    .joinToString(separator = separator) {
      it.requireFqName()
        .shortName()
        .asString()
    }

internal fun List<KotlinType>.getAllSuperTypes(): Sequence<FqName> =
  generateSequence(this) { kotlinTypes ->
    kotlinTypes.ifEmpty { null }?.flatMap { it.supertypes() }
  }
    .flatMap { it.asSequence() }
    .map { it.classDescriptorForType().fqNameSafe }

internal fun AnnotationDescriptor.isQualifier(): Boolean {
  return annotationClass?.annotations?.hasAnnotation(qualifierFqName) ?: false
}

internal fun AnnotationDescriptor.requireClass(): ClassDescriptor {
  return annotationClass ?: throw AnvilCompilationException(
    message = "Couldn't find the annotation class for $fqName",
  )
}
