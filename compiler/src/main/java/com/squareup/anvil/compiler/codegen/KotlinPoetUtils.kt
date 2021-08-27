package com.squareup.anvil.compiler.codegen

import com.squareup.anvil.compiler.assistedFqName
import com.squareup.anvil.compiler.daggerDoubleCheckFqNameString
import com.squareup.anvil.compiler.daggerLazyFqName
import com.squareup.anvil.compiler.internal.findAnnotation
import com.squareup.anvil.compiler.internal.fqNameOrNull
import com.squareup.anvil.compiler.internal.isNullable
import com.squareup.anvil.compiler.internal.requireTypeName
import com.squareup.anvil.compiler.internal.requireTypeReference
import com.squareup.anvil.compiler.internal.withJvmSuppressWildcardsIfNeeded
import com.squareup.anvil.compiler.providerFqName
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asClassName
import dagger.Lazy
import dagger.internal.ProviderOfLazy
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtTypeArgumentList
import org.jetbrains.kotlin.psi.KtTypeElement
import org.jetbrains.kotlin.psi.KtTypeProjection
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtValueArgument
import javax.inject.Provider

internal data class Parameter(
  val name: String,
  val typeName: TypeName,
  val providerTypeName: ParameterizedTypeName,
  val lazyTypeName: ParameterizedTypeName,
  val isWrappedInProvider: Boolean,
  val isWrappedInLazy: Boolean,
  val isLazyWrappedInProvider: Boolean,
  val isAssisted: Boolean,
  val assistedIdentifier: String,
  val assistedParameterKey: AssistedParameterKey = AssistedParameterKey(
    typeName,
    assistedIdentifier
  )
) {
  val originalTypeName: TypeName = when {
    isLazyWrappedInProvider -> lazyTypeName.wrapInProvider()
    isWrappedInProvider -> providerTypeName
    isWrappedInLazy -> lazyTypeName
    else -> typeName
  }

  // @Assisted parameters are equal, if the type and the identifier match. This subclass makes
  // diffing the parameters easier.
  data class AssistedParameterKey(
    private val typeName: TypeName,
    private val assistedIdentifier: String
  )
}

private fun KtTypeElement.singleTypeArgument(): KtTypeReference {
  return children
    .filterIsInstance<KtTypeArgumentList>()
    .single()
    .children
    .filterIsInstance<KtTypeProjection>()
    .single()
    .children
    .filterIsInstance<KtTypeReference>()
    .single()
}

internal fun List<KtCallableDeclaration>.mapToParameter(module: ModuleDescriptor): List<Parameter> =
  mapIndexed { index, parameter ->
    val typeElement = parameter.typeReference?.typeElement
    val typeFqName = typeElement?.fqNameOrNull(module)

    val isWrappedInProvider = typeFqName == providerFqName
    val isWrappedInLazy = typeFqName == daggerLazyFqName

    var isLazyWrappedInProvider = false

    val typeName = when {
      parameter.requireTypeReference(module).isNullable() ->
        parameter.requireTypeReference(module).requireTypeName(module).copy(nullable = true)

      isWrappedInProvider || isWrappedInLazy -> {
        val typeParameterReference = typeElement!!.singleTypeArgument()

        if (isWrappedInProvider &&
          typeParameterReference.fqNameOrNull(module) == daggerLazyFqName
        ) {
          // This is a super rare case when someone injects Provider<Lazy<Type>> that requires
          // special care.
          isLazyWrappedInProvider = true
          typeParameterReference.typeElement!!.singleTypeArgument().requireTypeName(module)
        } else {
          typeParameterReference.requireTypeName(module)
        }
      }

      else -> parameter.requireTypeReference(module).requireTypeName(module)
    }.withJvmSuppressWildcardsIfNeeded(parameter, module)

    val assistedAnnotation = parameter.findAnnotation(assistedFqName, module)
    val assistedIdentifier =
      (assistedAnnotation?.valueArguments?.firstOrNull() as? KtValueArgument)
        ?.children
        ?.filterIsInstance<KtStringTemplateExpression>()
        ?.single()
        ?.children
        ?.first()
        ?.text
        ?: ""

    Parameter(
      name = "param$index",
      typeName = typeName,
      providerTypeName = typeName.wrapInProvider(),
      lazyTypeName = typeName.wrapInLazy(),
      isWrappedInProvider = isWrappedInProvider,
      isWrappedInLazy = isWrappedInLazy,
      isLazyWrappedInProvider = isLazyWrappedInProvider,
      isAssisted = assistedAnnotation != null,
      assistedIdentifier = assistedIdentifier
    )
  }

/**
 * Converts the parameter list to comma separated argument list that can be used to call other
 * functions, e.g.
 * ```
 * [param0: String, param1: Int] -> "param0, param1"
 * ```
 * [asProvider] allows you to decide if each parameter is wrapped in a `Provider` interface. If
 * true, then the `get()` function will be called for the provider parameter. If false, then
 * then always only the parameter name will used in the argument list:
 * ```
 * "param0.get()" vs "param0"
 * ```
 * Set [includeModule] to true if a Dagger module instance is part of the argument list.
 */
internal fun List<Parameter>.asArgumentList(
  asProvider: Boolean,
  includeModule: Boolean
): String {
  return this
    .let { list ->
      if (asProvider) {
        list.map { parameter ->
          when {
            parameter.isLazyWrappedInProvider ->
              "${ProviderOfLazy::class.qualifiedName}.create(${parameter.name})"
            parameter.isWrappedInProvider -> parameter.name
            // Normally Dagger changes Lazy<Type> parameters to a Provider<Type> (usually the
            // container is a joined type), therefore we use `.lazy(..)` to convert the Provider
            // to a Lazy. Assisted parameters behave differently and the Lazy type is not changed
            // to a Provider and we can simply use the parameter name in the argument list.
            parameter.isWrappedInLazy && parameter.isAssisted -> parameter.name
            parameter.isWrappedInLazy -> "$daggerDoubleCheckFqNameString.lazy(${parameter.name})"
            parameter.isAssisted -> parameter.name
            else -> "${parameter.name}.get()"
          }
        }
      } else list.map { it.name }
    }
    .let {
      if (includeModule) {
        val result = it.toMutableList()
        result.add(0, "module")
        result.toList()
      } else {
        it
      }
    }
    .joinToString()
}

private fun TypeName.wrapInProvider(): ParameterizedTypeName {
  return Provider::class.asClassName().parameterizedBy(this)
}

private fun TypeName.wrapInLazy(): ParameterizedTypeName {
  return Lazy::class.asClassName().parameterizedBy(this)
}
