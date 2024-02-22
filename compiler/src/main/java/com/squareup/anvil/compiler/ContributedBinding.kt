package com.squareup.anvil.compiler

import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSType
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.compiler.codegen.ksp.resolveKSClassDeclaration
import com.squareup.anvil.compiler.codegen.reference.AnvilCompilationExceptionClassReferenceIr
import com.squareup.anvil.compiler.codegen.reference.ClassReferenceIr
import com.squareup.anvil.compiler.codegen.reference.find
import com.squareup.anvil.compiler.internal.reference.AnnotationReference
import com.squareup.anvil.compiler.internal.reference.ClassReference

private typealias Scope = ClassReferenceIr
private typealias BindingModule = ClassReferenceIr
private typealias OriginClass = ClassReferenceIr

internal data class BindingKey(
  val scope: Scope,
  val boundType: ClassReferenceIr,
  val qualifierKey: String,
)

/**
 * A data structure for organizing all contributed bindings with layering down through scopes and
 * binding keys.
 *
 * ```
 *
 *   Contributed Bindings
 *
 *     ┌──────────────────────────────────────────┐
 *     │                                          │
 *     │  Scope                                   │
 *     │                                          │
 *     │   ┌──────────────────────────────────┐   │
 *     │   │                                  │   │
 *     │   │  BindingKey (type + qualifier)   │   │
 *     │   │                                  │   │
 *     │   │    ┌────────────────────────┐    │   │
 *     │   │    │                        │    │   │
 *     │   │    │  Prioritized bindings  │    │   │
 *     │   │    │                        │    │   │
 *     │   │    └────────────────────────┘    │   │
 *     │   │                                  │   │
 *     │   │                                  │   │
 *     │   └──────────────────────────────────┘   │
 *     │                                          │
 *     │                                          │
 *     └──────────────────────────────────────────┘
 *
 *     ┌──────────────────────────────────────────┐
 *     │                                          │
 *     │  Scope2                                  │
 *     │                                          │
 *     │   ┌──────────────────────────────────┐   │
 *     │   │                                  │   │
 *     │   │  BindingKey (type + qualifier)   │   │
 *     │   │                                  │   │
 *     │   │    ┌────────────────────────┐    │   │
 *     │   │    │                        │    │   │
 *     │   │    │  Prioritized bindings  │    │   │
 *     │   │    │                        │    │   │
 *     │   │    └────────────────────────┘    │   │
 *     │   │                                  │   │
 *     │   │                                  │   │
 *     │   └──────────────────────────────────┘   │
 *     │                                          │
 *     │                                          │
 *     └──────────────────────────────────────────┘
 * ```
 */
internal data class ContributedBindings(
  val bindings: Map<Scope, Map<BindingKey, List<ContributedBinding>>>,
) {
  val bindingsByImplClass: Map<OriginClass, Map<Scope, List<ContributedBinding>>> = bindings.values.flatMap {
    it.values
  }.flatten()
    .groupBy { it.originClass }
    .mapValues { (_, values) ->
      values.groupBy { it.scope }
    }

  val replacedBindings = bindings.flatMapTo(mutableSetOf()) { (scope, bindingsForScope) ->
    bindingsForScope.flatMap { (_, bindings) ->
      val replacedBindings = bindings.flatMap { it.replaces }
      replacedBindings.plus(
        // Remap replaced bindings to their binding module, if relevant
        replacedBindings.mapNotNull { replacedBinding ->
          bindingsByImplClass[replacedBinding]?.get(scope)?.firstOrNull()?.bindingModule
        },
      )
    }
  }

  companion object {
    fun from(
      bindings: List<ContributedBinding>,
    ): ContributedBindings {
      val groupedByScope = bindings.groupBy { it.scope }
      val groupedByScopeAndKey = groupedByScope.mapValues { (_, bindings) ->
        bindings.groupBy { it.bindingKey }
          .mapValues { (_, bindings) ->
            bindings
              .sortedBy { it.priority }.also {
                // Check no duplicate priorities
                if (it.groupBy { it.priority }.values.any { it.size > 1 }) {
                  throw AnvilCompilationExceptionClassReferenceIr(
                    bindings[0].boundType,
                    "There are multiple contributed bindings with the same bound type. The bound type is " +
                      "${bindings[0].boundType.fqName.asString()}. The contributed binding classes are: " +
                      bindings.joinToString(
                        prefix = "[",
                        postfix = "]",
                      ) { "${it.originClass.fqName.asString()} (priority=${it.priority})" },
                  )
                }
              }
          }
      }

      return ContributedBindings(groupedByScopeAndKey)
    }
  }
}

internal data class ContributedBinding(
  val scope: Scope,
  val isMultibinding: Boolean,
  val bindingModule: BindingModule,
  val originClass: OriginClass,
  val boundType: ClassReferenceIr,
  val qualifierKey: String,
  val priority: ContributesBinding.Priority,
) {
  val bindingKey = BindingKey(scope, boundType, qualifierKey)
  val replaces = bindingModule.annotations.find(contributesToFqName).single()
    .replacedClasses
}

internal fun List<ContributedBinding>.findHighestPriorityBinding(): ContributedBinding {
  if (size == 1) return this[0]

  val bindings = groupBy { it.priority }
    .toSortedMap()
    .let { it.getValue(it.lastKey()) }
    // In some very rare cases we can see a binding for the same type twice. Just in case filter
    // them, see https://github.com/square/anvil/issues/460.
    .distinctBy { it.boundType }

  if (bindings.size > 1) {
    throw AnvilCompilationExceptionClassReferenceIr(
      bindings[0].boundType,
      "There are multiple contributed bindings with the same bound type. The bound type is " +
        "${bindings[0].boundType.fqName.asString()}. The contributed binding classes are: " +
        bindings.joinToString(
          prefix = "[",
          postfix = "]",
        ) { it.originClass.fqName.asString() },
    )
  }

  return bindings[0]
}

internal fun AnnotationReference.qualifierKey(): String {
  return fqName.asString() +
    arguments.joinToString(separator = "") { argument ->
      val valueString = when (val value = argument.value<Any>()) {
        is ClassReference -> value.fqName.asString()
        // TODO what if it's another annotation?
        else -> value.toString()
      }

      argument.resolvedName + valueString
    }
}

internal fun KSAnnotation.qualifierKey(): String {
  return shortName.asString() +
    arguments.joinToString(separator = "") { argument ->
      val valueString = when (val value = argument.value) {
        is KSType -> value.resolveKSClassDeclaration()!!.qualifiedName!!.asString()
        // TODO what if it's another annotation?
        else -> value.toString()
      }

      argument.name!!.asString() + valueString
    }
}
