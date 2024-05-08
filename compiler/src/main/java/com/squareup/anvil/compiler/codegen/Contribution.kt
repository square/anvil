package com.squareup.anvil.compiler.codegen

import com.squareup.anvil.annotations.ContributesTo
import com.squareup.anvil.annotations.internal.InternalBindingMarker
import com.squareup.anvil.compiler.BINDING_MODULE_SUFFIX
import com.squareup.anvil.compiler.MULTIBINDING_MODULE_SUFFIX
import com.squareup.anvil.compiler.codegen.dagger.ProvidesMethodFactoryCodeGen
import com.squareup.anvil.compiler.internal.capitalize
import com.squareup.anvil.compiler.internal.createAnvilSpec
import com.squareup.anvil.compiler.internal.joinSimpleNamesAndTruncate
import com.squareup.anvil.compiler.internal.safePackageString
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.joinToCode
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import dagger.multibindings.IntoSet

internal sealed class Contribution {
  abstract val origin: ClassName
  abstract val scope: ClassName
  abstract val isObject: Boolean
  abstract val boundType: ClassName
  abstract val replaces: List<ClassName>
  abstract val qualifier: QualifierData?
  abstract val bindingModuleNameSuffix: String

  /**
   * ex: `MyClassImpl_MyClass_AppScope_BindingModule_a1b2c3d4e5f6g7h8`
   */
  val className by lazy(LazyThreadSafetyMode.NONE) {
    uniqueTypeName(
      originType = origin,
      boundType = boundType,
      scopeType = scope,
      suffix = bindingModuleNameSuffix,
    )
  }

  val simpleName: String by lazy(LazyThreadSafetyMode.NONE) { className.simpleName }

  val functionName: String by lazy(LazyThreadSafetyMode.NONE) {
    val functionNameSuffix = boundType.simpleName.capitalize()
    if (isObject) {
      "provide$functionNameSuffix"
    } else {
      "bind$functionNameSuffix"
    }
  }

  // Can't be private until we move off jvmTarget 8
  fun generateBindingModule(): TypeSpec {
    val contribution = this

    val builder = if (contribution.isObject) {
      TypeSpec.objectBuilder(simpleName)
    } else {
      TypeSpec.interfaceBuilder(simpleName)
    }
    return builder.apply {
      addAnnotation(Module::class)
      addAnnotation(
        AnnotationSpec.builder(ContributesTo::class)
          .addMember("scope = %T::class", contribution.scope)
          .apply {
            if (contribution.replaces.isNotEmpty()) {
              addMember(
                "replaces = %L",
                contribution.replaces.map { CodeBlock.of("%T::class", it) }
                  .joinToCode(prefix = "[", suffix = "]"),
              )
            }
          }
          .build(),
      )

      addAnnotation(
        AnnotationSpec.builder(
          InternalBindingMarker::class.asClassName(),
        )
          .addMember("originClass = %T::class", contribution.origin)
          .addMember("isMultibinding = ${contribution is MultiBinding}")
          .apply {
            contribution.qualifier?.key?.let { qualifierKey ->
              addMember("qualifierKey = %S", qualifierKey)
            }
            if (contribution is Binding) {
              addMember(
                "rank = %L",
                contribution.rank,
              )
            }
          }
          .build(),
      )

      val nameSuffix = contribution.boundType.simpleName.capitalize()
      val functionBuilder = if (contribution.isObject) {
        FunSpec.builder("provide$nameSuffix")
          .addAnnotation(Provides::class)
          .addStatement("return %T", origin)
      } else {
        FunSpec.builder("bind$nameSuffix")
          .addModifiers(KModifier.ABSTRACT)
          .addAnnotation(Binds::class)
          .addParameter("real", origin)
      }

      addFunction(
        functionBuilder
          .apply {
            contribution.qualifier?.let { addAnnotation(it.annotationSpec) }
            if (contribution is MultiBinding) {
              if (contribution.mapKey == null) {
                addAnnotation(IntoSet::class)
              } else {
                addAnnotation(IntoMap::class)
                addAnnotation(contribution.mapKey)
              }
            }
          }
          .returns(contribution.boundType)
          .build(),
      )
    }.build()
  }

  companion object {
    // The generic ensures the list is of the same subtype
    fun <T : Contribution> List<T>.generateFileSpecs(
      generateProviderFactories: Boolean,
    ): List<FileSpec> {

      val contributions = this@generateFileSpecs
      val origin = first().origin
      val generatedPackage = origin.packageName.safePackageString(dotPrefix = true)
      return buildList {
        contributions.distinct()
          // Give it a stable sort.
          .sortedWith(COMPARATOR)
          .forEach { contribution ->

            val moduleType = contribution.generateBindingModule()
            val moduleFile = FileSpec.createAnvilSpec(generatedPackage, moduleType.name!!) {
              addType(moduleType)
            }
            add(moduleFile)

            if (contribution.isObject && generateProviderFactories) {

              val providerFactoryFile = generateProviderFactory(
                contribution = contribution,
                origin = origin,
                generatedPackage = generatedPackage,
              )
              add(providerFactoryFile)
            }
          }
      }
    }

    private fun <T : Contribution> generateProviderFactory(
      contribution: T,
      origin: ClassName,
      generatedPackage: String,
    ): FileSpec {
      val declaration = ProvidesMethodFactoryCodeGen.CallableReference(
        isInternal = false,
        isCompanionObject = false,
        name = contribution.functionName,
        isProperty = false,
        constructorParameters = emptyList(),
        type = contribution.boundType,
        isNullable = origin.isNullable,
        isPublishedApi = false,
        reportableNode = contribution.functionName,
      )
      return ProvidesMethodFactoryCodeGen.generateFactoryClass(
        isMangled = false,
        // In this case, the suffix doesn't matter since the provider will never be mangled.
        mangledNameSuffix = "",
        moduleClass = ClassName(generatedPackage, contribution.simpleName),
        isInObject = true,
        declaration = declaration,
      )
    }

    private val COMPARATOR: Comparator<Contribution> = compareBy<Contribution> {
      it.scope.canonicalName
    }
      .thenComparing(compareBy { it.boundType.canonicalName })
      .thenComparing(compareBy { if (it is Binding) it.rank else 0 })
      .thenComparing(compareBy { it.replaces.joinToString(transform = ClassName::canonicalName) })

    internal fun uniqueTypeName(
      originType: ClassName,
      boundType: ClassName,
      scopeType: ClassName,
      suffix: String,
    ): ClassName {
      val types = listOf(originType, boundType, scopeType)
      return ClassName(
        packageName = originType.packageName,
        simpleNames = types.map { it.simpleName } + suffix,
      )
        .joinSimpleNamesAndTruncate(
          hashParams = types + suffix,
          separator = "_",
        )
    }
  }

  data class QualifierData(
    val annotationSpec: AnnotationSpec,
    val key: String,
  )

  data class Binding(
    override val origin: ClassName,
    override val scope: ClassName,
    override val isObject: Boolean,
    override val boundType: ClassName,
    val rank: Int,
    override val replaces: List<ClassName>,
    override val qualifier: QualifierData?,
  ) : Contribution() {
    override val bindingModuleNameSuffix: String = BINDING_MODULE_SUFFIX
  }

  data class MultiBinding(
    override val origin: ClassName,
    override val scope: ClassName,
    override val isObject: Boolean,
    override val boundType: ClassName,
    override val replaces: List<ClassName>,
    override val qualifier: QualifierData?,
    val mapKey: AnnotationSpec?,
  ) : Contribution() {
    override val bindingModuleNameSuffix: String = MULTIBINDING_MODULE_SUFFIX
  }
}
