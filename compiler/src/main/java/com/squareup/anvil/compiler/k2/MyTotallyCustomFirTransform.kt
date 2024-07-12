package com.squareup.anvil.compiler.k2

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.FirExtension
import org.jetbrains.kotlin.fir.extensions.FirExtensionPointName
import org.jetbrains.kotlin.fir.extensions.FirExtensionService
import org.jetbrains.kotlin.fir.extensions.predicate.DeclarationPredicate
import org.jetbrains.kotlin.name.FqName
import kotlin.reflect.KClass

public class MyTotallyCustomFirTransform(session: FirSession) : FirExtension(session) {

  public companion object {
    public val NAME: FirExtensionPointName = FirExtensionPointName("TotallyCustom")
  }

  override val name: FirExtensionPointName get() = NAME

  override val extensionType: KClass<out FirExtension> = MyTotallyCustomFirTransform::class

  public fun doSomething() {
    error("doSomething")
  }

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    register(DeclarationPredicate.create { hasAnnotated(FqName("foo.Freddy")) })
  }
}

public val FirExtensionService.assignTotallyCustomExtensions: List<MyTotallyCustomFirTransform> by FirExtensionService.registeredExtensions()
