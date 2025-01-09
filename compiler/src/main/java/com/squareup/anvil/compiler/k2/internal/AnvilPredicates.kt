package com.squareup.anvil.compiler.k2.internal

import com.squareup.anvil.compiler.k2.internal.Names.anvil
import org.jetbrains.kotlin.fir.extensions.predicate.LookupPredicate
import org.jetbrains.kotlin.name.FqName

internal object AnvilPredicates {
  val hasComponentAnnotation
    get() = Names.dagger.component.annotated
  val hasInjectAnnotation
    get() = Names.inject.annotated
  val hasMergeComponentFirAnnotation
    get() = anvil.mergeComponent.annotated

  private val FqName.annotated: LookupPredicate
    get() = LookupPredicate.create { annotated(this@annotated) }
}
