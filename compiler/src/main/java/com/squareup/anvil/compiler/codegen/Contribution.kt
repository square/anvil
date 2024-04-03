package com.squareup.anvil.compiler.codegen

import com.squareup.anvil.annotations.ContributesTo
import com.squareup.anvil.annotations.internal.InternalBindingMarker
import com.squareup.anvil.compiler.BINDING_MODULE_SUFFIX
import com.squareup.anvil.compiler.MULTIBINDING_MODULE_SUFFIX
import com.squareup.anvil.compiler.internal.capitalize
import com.squareup.anvil.compiler.internal.createAnvilSpec
import com.squareup.anvil.compiler.internal.reference.generateClassName
import com.squareup.anvil.compiler.internal.reference.generateClassNameString
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

internal sealed interface Contribution {
  val origin: ClassName
  val scope: ClassName
  val isObject: Boolean
  val boundType: ClassName
  val replaces: List<ClassName>
  val qualifier: QualifierData?
  val bindingModuleNameSuffix: String

  // Can't be private until we move off jvmTarget 8
  fun generateBindingModule(): TypeSpec {
    val contribution = this
    // Combination name of origin, scope, and boundType
    val suffix = "As" +
      contribution.boundType.generateClassNameString().capitalize() +
      "To" +
      contribution.scope.generateClassNameString().capitalize() +
      bindingModuleNameSuffix

    val contributionName =
      origin.generateClassName(suffix = suffix).simpleName
    val builder = if (contribution.isObject) {
      TypeSpec.objectBuilder(contributionName)
    } else {
      TypeSpec.interfaceBuilder(contributionName)
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
                "priority = %L",
                contribution.priority,
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
    fun <T : Contribution> List<T>.generateFileSpecs(): List<FileSpec> {
      val origin = first().origin
      val generatedPackage = origin.packageName.safePackageString(dotPrefix = true)
      // Give it a stable sort.
      return distinct()
        .sortedWith(COMPARATOR)
        .map(Contribution::generateBindingModule)
        .map { spec ->
          FileSpec.createAnvilSpec(generatedPackage, spec.name!!) {
            addType(spec)
          }
        }
    }

    private val COMPARATOR: Comparator<Contribution> = compareBy<Contribution> {
      it.scope.canonicalName
    }
      .thenComparing(compareBy { it.boundType.canonicalName })
      .thenComparing(compareBy { if (it is Binding) it.priority else 0 })
      .thenComparing(compareBy { it.replaces.joinToString(transform = ClassName::canonicalName) })
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
    val priority: Int,
    override val replaces: List<ClassName>,
    override val qualifier: QualifierData?,
  ) : Contribution {
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
  ) : Contribution {
    override val bindingModuleNameSuffix: String = MULTIBINDING_MODULE_SUFFIX
  }
}
