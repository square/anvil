package com.squareup.anvil.compiler.k2.names

import org.jetbrains.kotlin.name.FqName

internal object Names {
  val daggerFactory = FqName("dagger.internal.Factory")

  val kotlinJvmStatic = FqName("kotlin.jvm.JvmStatic")

  val javaxInject = FqName("javax.inject.Inject")
  val javaxProvider = FqName("javax.inject.Provider")
}
