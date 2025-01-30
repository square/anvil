package com.squareup.anvil.compiler.testing

import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.anvil.annotations.ContributesSubcomponent
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.anvil.annotations.MergeComponent
import com.squareup.anvil.annotations.MergeSubcomponent
import com.squareup.anvil.annotations.compat.MergeInterfaces
import com.squareup.anvil.annotations.compat.MergeModules
import com.squareup.anvil.annotations.internal.InternalBindingMarker
import com.squareup.kotlinpoet.asClassName
import dagger.Binds
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
import org.jetbrains.kotlin.name.FqName
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Qualifier
import kotlin.reflect.KClass

internal val KClass<*>.fqName: FqName
  get() = FqName(requireNotNull(qualifiedName))

internal val mergeComponentFqName = MergeComponent::class.fqName
internal val mergeSubcomponentFqName = MergeSubcomponent::class.fqName
internal val mergeInterfacesFqName = MergeInterfaces::class.fqName
internal val mergeModulesFqName = MergeModules::class.fqName
internal val contributesToFqName = ContributesTo::class.fqName
internal val contributesBindingFqName = ContributesBinding::class.fqName
internal val contributesMultibindingFqName = ContributesMultibinding::class.fqName
internal val contributesSubcomponentFqName = ContributesSubcomponent::class.fqName
internal val contributesSubcomponentFactoryFqName = ContributesSubcomponent.Factory::class.fqName
internal val internalBindingMarkerFqName = InternalBindingMarker::class.fqName
internal val daggerComponentFqName = Component::class.fqName
internal val daggerSubcomponentFqName = Subcomponent::class.fqName
internal val daggerSubcomponentFactoryFqName = Subcomponent.Factory::class.fqName
internal val daggerSubcomponentBuilderFqName = Subcomponent.Builder::class.fqName
internal val daggerModuleFqName = Module::class.fqName
internal val daggerBindsFqName = Binds::class.fqName
internal val daggerProvidesFqName = Provides::class.fqName
internal val daggerLazyFqName = Lazy::class.fqName
internal val daggerLazyClassName = Lazy::class.asClassName()
internal val injectFqName = Inject::class.fqName
internal val qualifierFqName = Qualifier::class.fqName
internal val mapKeyFqName = MapKey::class.fqName
internal val assistedFqName = Assisted::class.fqName
internal val assistedFactoryFqName = AssistedFactory::class.fqName
internal val assistedInjectFqName = AssistedInject::class.fqName
internal val providerFqName = Provider::class.fqName
internal val providerClassName = Provider::class.asClassName()
internal val jvmSuppressWildcardsFqName = JvmSuppressWildcards::class.fqName
internal val jvmFieldFqName = JvmField::class.fqName
internal val publishedApiFqName = PublishedApi::class.fqName
internal val anyFqName = Any::class.fqName

internal val daggerDoubleCheckFqNameString = DoubleCheck::class.java.canonicalName

internal val isWordPrefixRegex = "^is([^a-z].*)".toRegex()

internal const val HINT_PACKAGE = "anvil.hint"

internal const val BINDING_MODULE_SUFFIX = "BindingModule"
internal const val MULTIBINDING_MODULE_SUFFIX = "MultiBindingModule"

internal const val PARENT_COMPONENT = "ParentComponent"
internal const val SUBCOMPONENT_FACTORY = "SubcomponentFactory"
internal const val SUBCOMPONENT_MODULE = "SubcomponentModule"
internal const val COMPONENT_PACKAGE_PREFIX = "anvil.component"

internal const val REFERENCE_SUFFIX = "_reference"
internal const val SCOPE_SUFFIX = "_scope"

/**
 * Returns the single element matching the given [predicate], or `null` if element was not found.
 * Unlike [singleOrNull] this method throws an exception if more than one element is found.
 */
internal inline fun <T> Iterable<T>.singleOrEmpty(predicate: (T) -> Boolean): T? {
  var single: T? = null
  var found = false
  for (element in this) {
    if (predicate(element)) {
      if (found) {
        throw IllegalArgumentException(
          "Collection contains more than one matching element.",
        )
      }
      single = element
      found = true
    }
  }
  return single
}

private val truePredicate: (Any?) -> Boolean = { true }

/**
 * Returns single element, or `null` if the collection is empty. Unlike [singleOrNull] this
 * method throws an exception if more than one element is found.
 */
internal fun <T> Iterable<T>.singleOrEmpty(): T? = singleOrEmpty(truePredicate)
