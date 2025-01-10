package com.squareup.anvil.compiler.k2.internal

import com.squareup.anvil.compiler.k2.internal.Names.anvil
import org.jetbrains.kotlin.fir.extensions.predicate.LookupPredicate
import org.jetbrains.kotlin.name.FqName

internal object AnvilPredicates {
  val hasComponentAnnotation
    get() = Names.dagger.component.lookupPredicateAnnotated()
  val hasInjectAnnotation
    get() = Names.inject.lookupPredicateAnnotated()
  val hasMergeComponentFirAnnotation
    get() = anvil.mergeComponent.lookupPredicateAnnotated()

  private fun FqName.lookupPredicateAnnotated(): LookupPredicate =
    LookupPredicate.create { annotated(this@lookupPredicateAnnotated) }
}
