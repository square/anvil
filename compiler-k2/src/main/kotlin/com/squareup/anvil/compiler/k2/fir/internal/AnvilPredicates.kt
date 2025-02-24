package com.squareup.anvil.compiler.k2.fir.internal

import com.squareup.anvil.compiler.k2.utils.names.ClassIds
import org.jetbrains.kotlin.fir.extensions.predicate.DeclarationPredicate
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

  val hasContributesBindingAnnotation
    get() = ClassIds.anvilContributesBinding.lookupPredicateAnnotated()

  val hasComponentAnnotation
    get() = ClassIds.daggerComponent.lookupPredicateAnnotated()
  val hasInjectAnnotation
    get() = ClassIds.javaxInject.lookupPredicateAnnotated()

  val hasContributesSubcomponentAnnotation
    get() = ClassIds.anvilContributesSubcomponent.lookupPredicateAnnotated()

  private fun ClassId.declarationPredicateAnnotated(): DeclarationPredicate =
    DeclarationPredicate.create { annotated(this@declarationPredicateAnnotated.asSingleFqName()) }

  private fun ClassId.lookupPredicateAnnotated(): LookupPredicate =
    LookupPredicate.create { annotated(this@lookupPredicateAnnotated.asSingleFqName()) }
}
