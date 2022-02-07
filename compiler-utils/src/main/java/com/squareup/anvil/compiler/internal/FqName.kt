package com.squareup.anvil.compiler.internal

import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.anvil.annotations.ContributesSubcomponent
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.anvil.annotations.ExperimentalAnvilApi
import dagger.Lazy
import dagger.MapKey
import dagger.Provides
import org.jetbrains.kotlin.name.FqName
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Scope

internal val jvmSuppressWildcardsFqName = JvmSuppressWildcards::class.fqName
internal val publishedApiFqName = PublishedApi::class.fqName
internal val qualifierFqName = Qualifier::class.fqName
internal val mapKeyFqName = MapKey::class.fqName
internal val daggerProvidesFqName = Provides::class.fqName
internal val daggerLazyFqName = Lazy::class.fqName
internal val daggerScopeFqName = Scope::class.fqName
internal val injectFqName = Inject::class.fqName

internal val contributesToFqName = ContributesTo::class.fqName
internal val contributesBindingFqName = ContributesBinding::class.fqName
internal val contributesMultibindingFqName = ContributesMultibinding::class.fqName
internal val contributesSubcomponentFqName = ContributesSubcomponent::class.fqName

@ExperimentalAnvilApi
public fun FqName.descendant(segments: String): FqName =
  if (isRoot) FqName(segments) else FqName("${asString()}.$segments")
