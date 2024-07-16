package com.squareup.anvil.compiler

import com.squareup.anvil.compiler.internal.capitalize
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier.ABSTRACT
import com.squareup.kotlinpoet.TypeSpec
import dagger.Binds
import dagger.Module

/**
 * A binding spec for use with [daggerBindingModuleSpec].
 */
internal data class BindingSpec(
  val impl: ClassName,
  val boundType: ClassName,
  val functionName: String = "bind${impl.simpleName.capitalize()}",
)

/**
 * A [TypeSpec] factory for a Dagger binding module.
 *
 * @param specs a collection of [BindingSpec]s to generate binding functions for.
 */
internal fun daggerBindingModuleSpec(
  moduleName: String,
  specs: Collection<BindingSpec>,
): TypeSpec {
  check(specs.isNotEmpty())
  return TypeSpec
    .interfaceBuilder(moduleName)
    .addAnnotation(Module::class)
    .apply {
      for (spec in specs) {
        addFunction(
          FunSpec
            .builder(spec.functionName)
            .addAnnotation(Binds::class)
            .addModifiers(ABSTRACT)
            .addParameter("impl", spec.impl)
            .returns(spec.boundType)
            .build(),
        )
      }
    }
    .build()
}
