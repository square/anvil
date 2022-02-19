package com.squareup.anvil.compiler

import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.anvil.annotations.ContributesSubcomponent
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.anvil.annotations.MergeComponent
import com.squareup.anvil.annotations.MergeSubcomponent
import com.squareup.anvil.annotations.compat.MergeInterfaces
import com.squareup.anvil.annotations.compat.MergeModules
import com.squareup.anvil.compiler.internal.fqName
import com.squareup.anvil.compiler.internal.reference.ClassReference
import com.squareup.anvil.compiler.internal.reference.toClassReferenceOrNull
import dagger.Component
import dagger.Lazy
import dagger.MapKey
import dagger.Module
import dagger.Provides
import dagger.Subcomponent
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.internal.DoubleCheck
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.descriptorUtil.annotationClass
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Qualifier

internal val mergeComponentFqName = MergeComponent::class.fqName
internal val mergeSubcomponentFqName = MergeSubcomponent::class.fqName
internal val mergeInterfacesFqName = MergeInterfaces::class.fqName
internal val mergeModulesFqName = MergeModules::class.fqName
internal val contributesToFqName = ContributesTo::class.fqName
internal val contributesBindingFqName = ContributesBinding::class.fqName
internal val contributesMultibindingFqName = ContributesMultibinding::class.fqName
internal val contributesSubcomponentFqName = ContributesSubcomponent::class.fqName
internal val contributesSubcomponentFactoryFqName = ContributesSubcomponent.Factory::class.fqName
internal val daggerComponentFqName = Component::class.fqName
internal val daggerSubcomponentFqName = Subcomponent::class.fqName
internal val daggerSubcomponentFactoryFqName = Subcomponent.Factory::class.fqName
internal val daggerSubcomponentBuilderFqName = Subcomponent.Builder::class.fqName
internal val daggerModuleFqName = Module::class.fqName
internal val daggerProvidesFqName = Provides::class.fqName
internal val daggerLazyFqName = Lazy::class.fqName
internal val injectFqName = Inject::class.fqName
internal val qualifierFqName = Qualifier::class.fqName
internal val mapKeyFqName = MapKey::class.fqName
internal val assistedFqName = Assisted::class.fqName
internal val assistedFactoryFqName = AssistedFactory::class.fqName
internal val assistedInjectFqName = AssistedInject::class.fqName
internal val providerFqName = Provider::class.fqName
internal val jvmSuppressWildcardsFqName = JvmSuppressWildcards::class.fqName
internal val publishedApiFqName = PublishedApi::class.fqName

internal val daggerDoubleCheckFqNameString = DoubleCheck::class.java.canonicalName

internal const val HINT_CONTRIBUTES_PACKAGE_PREFIX = "anvil.hint.merge"
internal const val HINT_BINDING_PACKAGE_PREFIX = "anvil.hint.binding"
internal const val HINT_MULTIBINDING_PACKAGE_PREFIX = "anvil.hint.multibinding"
internal const val HINT_SUBCOMPONENTS_PACKAGE_PREFIX = "anvil.hint.subcomponent"
internal const val MODULE_PACKAGE_PREFIX = "anvil.module"
internal const val COMPONENT_PACKAGE_PREFIX = "anvil.component"

internal const val ANVIL_MODULE_SUFFIX = "AnvilModule"

// The suffix is a letter by design. Class names for subcomponents must be kept short.
internal const val ANVIL_SUBCOMPONENT_SUFFIX = "A"
internal const val PARENT_COMPONENT = "ParentComponent"
internal const val SUBCOMPONENT_FACTORY = "SubcomponentFactory"
internal const val SUBCOMPONENT_MODULE = "SubcomponentModule"

internal const val REFERENCE_SUFFIX = "_reference"
internal const val SCOPE_SUFFIX = "_scope"
internal val propertySuffixes = arrayOf(REFERENCE_SUFFIX, SCOPE_SUFFIX)

internal fun FqName.isAnvilModule(): Boolean {
  val name = asString()
  return name.startsWith(MODULE_PACKAGE_PREFIX) && name.endsWith(ANVIL_MODULE_SUFFIX)
}

@Suppress("DeprecatedCallableAddReplaceWith")
@Deprecated(
  "Don't rely on descriptors and make the code agnostic to the underlying implementation. " +
    "See [AnnotationReference#isMapKey]"
)
internal fun AnnotationDescriptor.isMapKey(): Boolean {
  return annotationClass?.annotations?.hasAnnotation(mapKeyFqName) ?: false
}

internal inline fun <reified T : ClassReference> ClassId.classReferenceOrNull(
  module: ModuleDescriptor
): T? =
  asSingleFqName().toClassReferenceOrNull(module) as T?
