package com.squareup.anvil.compiler.internal.reference

import com.squareup.anvil.annotations.ExperimentalAnvilApi
import org.jetbrains.kotlin.name.FqName

/**
 * This marks any code reference that can be annotated, such as a [ClassReference] or
 * [FunctionReference].
 */
@ExperimentalAnvilApi
public interface AnnotatedReference {
  public val annotations: List<AnnotationReference>

  public fun isAnnotatedWith(fqName: FqName): Boolean = annotations.any { it.fqName == fqName }
}
