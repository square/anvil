package com.squareup.anvil.compiler.internal

import dagger.Provides
import org.jetbrains.kotlin.name.FqName
import javax.inject.Inject
import javax.inject.Qualifier

internal val jvmSuppressWildcardsFqName = FqName(JvmSuppressWildcards::class.java.canonicalName)
internal val publishedApiFqName = FqName(PublishedApi::class.java.canonicalName)
internal val qualifierFqName = FqName(Qualifier::class.java.canonicalName)
internal val daggerProvidesFqName = FqName(Provides::class.java.canonicalName)
internal val injectFqName = FqName(Inject::class.java.canonicalName)
