package com.squareup.anvil.compiler.codegen

import com.squareup.anvil.compiler.codegen.Parameter.AssistedParameterKey
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.TypeName
import org.jetbrains.kotlin.name.FqName

sealed interface Parameter {
  val name: String
  val typeName: TypeName
  val providerTypeName: ParameterizedTypeName
  val lazyTypeName: ParameterizedTypeName
  val isWrappedInProvider: Boolean
  val isWrappedInLazy: Boolean
  val isLazyWrappedInProvider: Boolean
  val isAssisted: Boolean
  val assistedIdentifier: String
  val assistedParameterKey: AssistedParameterKey

  // @Assisted parameters are equal, if the type and the identifier match. This subclass makes
  // diffing the parameters easier.
  data class AssistedParameterKey(
    private val typeName: TypeName,
    private val assistedIdentifier: String
  )

  val originalTypeName: TypeName
    get() = when {
      isLazyWrappedInProvider -> lazyTypeName.wrapInProvider()
      isWrappedInProvider -> providerTypeName
      isWrappedInLazy -> lazyTypeName
      else -> typeName
    }
}

internal data class ConstructorParameter(
  override val name: String,
  override val typeName: TypeName,
  override val providerTypeName: ParameterizedTypeName,
  override val lazyTypeName: ParameterizedTypeName,
  override val isWrappedInProvider: Boolean,
  override val isWrappedInLazy: Boolean,
  override val isLazyWrappedInProvider: Boolean,
  override val isAssisted: Boolean,
  override val assistedIdentifier: String,
  override val assistedParameterKey: AssistedParameterKey = AssistedParameterKey(
    typeName,
    assistedIdentifier
  )
) : Parameter

internal data class MemberInjectParameter(
  override val name: String,
  override val typeName: TypeName,
  override val providerTypeName: ParameterizedTypeName,
  override val lazyTypeName: ParameterizedTypeName,
  override val isWrappedInProvider: Boolean,
  override val isWrappedInLazy: Boolean,
  override val isLazyWrappedInProvider: Boolean,
  override val isAssisted: Boolean,
  override val assistedIdentifier: String,
  val memberInjectorClassName: ClassName,
  val originalName: String,
  val isSetterInjected: Boolean,
  val accessName: String,
  val qualifierAnnotationSpecs: List<AnnotationSpec>,
  val injectedFieldSignature: FqName,
  override val assistedParameterKey: AssistedParameterKey = AssistedParameterKey(
    typeName,
    assistedIdentifier
  )
) : Parameter
