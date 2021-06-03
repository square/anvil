package com.squareup.anvil.compiler.internal

import dagger.Provides
import javax.inject.Inject
import javax.inject.Qualifier

internal val jvmSuppressWildcardsFqName = JvmSuppressWildcards::class.fqName
internal val publishedApiFqName = PublishedApi::class.fqName
internal val qualifierFqName = Qualifier::class.fqName
internal val daggerProvidesFqName = Provides::class.fqName
internal val injectFqName = Inject::class.fqName
