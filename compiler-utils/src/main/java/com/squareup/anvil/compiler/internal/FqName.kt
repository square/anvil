package com.squareup.anvil.compiler.internal

import dagger.Lazy
import dagger.MapKey
import dagger.Provides
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
