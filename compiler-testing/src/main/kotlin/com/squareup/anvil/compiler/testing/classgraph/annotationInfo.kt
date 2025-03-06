package com.squareup.anvil.compiler.testing.classgraph

import com.squareup.anvil.compiler.k2.utils.names.Names
import io.github.classgraph.AnnotationClassRef
import io.github.classgraph.AnnotationInfo
import io.kotest.matchers.collections.shouldContain
import org.jetbrains.kotlin.name.Name

/** Returns the fully qualified name of the Anvil scope argument */
public val AnnotationInfo.scope: String
  get() = requireParameterAs<AnnotationClassRef>(Names.scope).name

/** Returns all Dagger modules specified by the Component as fully qualified names */
public val AnnotationInfo.moduleNames: List<String>
  get() = requireParameterAs<Array<*>>(
    Names.modules,
  ).map { (it as AnnotationClassRef).name }.sorted()

/** Returns all Dagger modules specified by the Component as fully qualified names */
public val AnnotationInfo.hints: List<String>
  get() = requireParameterAs<Array<String>>(Names.hints).sorted()

/** Returns a value parameter named `name` from the annotation, or throws if it doesn't exist. */
public fun AnnotationInfo.requireParameter(name: Name, includeDefaultValues: Boolean = true): Any {
  getParameterValues(includeDefaultValues).names shouldContain name.identifier
  return getParameterValues(includeDefaultValues).getValue(name.identifier)
}

/** Returns a value parameter named `name` from the annotation, or throws if it doesn't exist. */
public inline fun <reified T> AnnotationInfo.requireParameterAs(
  name: Name,
  includeDefaultValues: Boolean = true,
): T = requireParameter(name, includeDefaultValues) as T
