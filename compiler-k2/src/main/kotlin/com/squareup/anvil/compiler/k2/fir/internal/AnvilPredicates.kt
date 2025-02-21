package com.squareup.anvil.compiler.k2.fir.internal

import com.squareup.anvil.compiler.k2.utils.names.ClassIds
import org.jetbrains.kotlin.fir.extensions.predicate.DeclarationPredicate
import org.jetbrains.kotlin.fir.extensions.predicate.LookupPredicate
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

internal object AnvilPredicates {
  val hasComponentAnnotation
    get() = ClassIds.daggerComponent.lookupPredicateAnnotated()
  val hasModuleAnnotation
    get() = ClassIds.daggerModule.lookupPredicateAnnotated()
  val hasInjectAnnotation
    get() = ClassIds.javaxInject.lookupPredicateAnnotated()

  val contributedModule
    get() = LookupPredicate.create {
      annotated(ClassIds.anvilContributesTo.asSingleFqName()).and(annotated(ClassIds.daggerModule.asSingleFqName()))
    }

  val hasContributesSubcomponentAnnotation
    get() = ClassIds.anvilContributesSubcomponent.lookupPredicateAnnotated()
  val hasContributesToAnnotation
    get() = ClassIds.anvilContributesTo.lookupPredicateAnnotated()

  val hasMergeComponentAnnotation
    get() = ClassIds.anvilMergeComponent.lookupPredicateAnnotated()

  private fun ClassId.lookupPredicateAnnotated(): LookupPredicate =
    LookupPredicate.create { annotated(this@lookupPredicateAnnotated.asSingleFqName()) }

  private fun ClassId.declarationPredicateAnnotated(): DeclarationPredicate =
    DeclarationPredicate.create { annotated(this@declarationPredicateAnnotated.asSingleFqName()) }

  private fun FqName.lookupPredicateAnnotated(): LookupPredicate =
    LookupPredicate.create { annotated(this@lookupPredicateAnnotated) }

  private fun FqName.declarationPredicateAnnotated(): DeclarationPredicate =
    DeclarationPredicate.create { annotated(this@declarationPredicateAnnotated) }
}
