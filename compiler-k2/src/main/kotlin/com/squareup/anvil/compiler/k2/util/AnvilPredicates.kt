package com.squareup.anvil.compiler.k2.util

import com.squareup.anvil.compiler.k2.utils.names.ClassIds
import org.jetbrains.kotlin.fir.extensions.predicate.LookupPredicate
import org.jetbrains.kotlin.name.ClassId

internal object AnvilPredicates {
  val hasContributesBindingAnnotation
    get() = ClassIds.anvilContributesBinding.lookupPredicateAnnotated()

  private fun ClassId.lookupPredicateAnnotated(): LookupPredicate =
    LookupPredicate.create { annotated(this@lookupPredicateAnnotated.asSingleFqName()) }
}
