package com.squareup.anvil.compiler.k2.fir

import com.squareup.anvil.compiler.k2.utils.names.ClassIds
import org.jetbrains.kotlin.fir.extensions.predicate.LookupPredicate
import org.jetbrains.kotlin.name.ClassId

internal object AnvilPredicates {
  val hasModuleAnnotation
    get() = ClassIds.daggerModule.lookupPredicateAnnotated()

  val contributedModule
    get() = LookupPredicate.create {
      annotated(ClassIds.anvilContributesTo.asSingleFqName())
        .and(annotated(ClassIds.daggerModule.asSingleFqName()))
    }

  val hasContributesToAnnotation
    get() = ClassIds.anvilContributesTo.lookupPredicateAnnotated()

  val hasMergeComponentAnnotation
    get() = ClassIds.anvilMergeComponent.lookupPredicateAnnotated()

  private fun ClassId.lookupPredicateAnnotated(): LookupPredicate =
    LookupPredicate.create { annotated(this@lookupPredicateAnnotated.asSingleFqName()) }
}
