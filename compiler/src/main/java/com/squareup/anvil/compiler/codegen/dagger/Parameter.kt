package com.squareup.anvil.compiler.codegen.dagger

import com.squareup.anvil.compiler.codegen.dagger.Parameter.AssistedParameterKey
import com.squareup.anvil.compiler.javaxProviderClassName
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.TypeName
import org.jetbrains.kotlin.name.FqName

internal sealed interface Parameter {
  val name: String
  val originalName: String
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
    private val assistedIdentifier: String,
  )

  val originalTypeName: TypeName
    get() = when {
      isLazyWrappedInProvider -> lazyTypeName.wrapInProvider(javaxProviderClassName)
      isWrappedInProvider -> providerTypeName
      isWrappedInLazy -> lazyTypeName
      else -> typeName
    }
}

/**
 * Returns a name which is unique when compared to the [Parameter.originalName] of the
 * [superParameters] argument.
 *
 * This is necessary for member-injected parameters, because a subclass may override a parameter
 * which is member-injected in the super.  The `MembersInjector` corresponding to the subclass must
 * have unique constructor parameters for each declaration, so their names must be unique.
 *
 * This mimics Dagger's method of unique naming.  If there are three parameters named "foo", the
 * unique parameter names will be [foo, foo2, foo3].
 */
internal fun String.uniqueParameterName(
  vararg superParameters: List<Parameter>,
): String {

  val numDuplicates = superParameters.sumOf { list ->
    list.count { it.originalName == this }
  }

  return if (numDuplicates == 0) {
    this
  } else {
    this + (numDuplicates + 1)
  }
}

internal data class ConstructorParameter(
  override val name: String,
  override val originalName: String,
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
    assistedIdentifier,
  ),
) : Parameter

internal data class MemberInjectParameter(
  override val name: String,
  override val originalName: String,
  override val typeName: TypeName,
  override val providerTypeName: ParameterizedTypeName,
  override val lazyTypeName: ParameterizedTypeName,
  override val isWrappedInProvider: Boolean,
  override val isWrappedInLazy: Boolean,
  override val isLazyWrappedInProvider: Boolean,
  override val isAssisted: Boolean,
  override val assistedIdentifier: String,
  val memberInjectorClassName: ClassName,
  val isSetterInjected: Boolean,
  val accessName: String,
  val qualifierAnnotationSpecs: List<AnnotationSpec>,
  val injectedFieldSignature: FqName,
  override val assistedParameterKey: AssistedParameterKey = AssistedParameterKey(
    typeName,
    assistedIdentifier,
  ),
  val resolvedProviderTypeName: ParameterizedTypeName = providerTypeName,
) : Parameter
