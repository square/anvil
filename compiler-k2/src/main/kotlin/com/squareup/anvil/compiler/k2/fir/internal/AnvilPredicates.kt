package com.squareup.anvil.compiler.k2.fir.internal

import com.squareup.anvil.compiler.k2.utils.names.ClassIds
import org.jetbrains.kotlin.fir.extensions.predicate.DeclarationPredicate
import org.jetbrains.kotlin.fir.extensions.predicate.LookupPredicate
import org.jetbrains.kotlin.fir.extensions.predicate.LookupPredicate.BuilderContext.or
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

internal object AnvilPredicates {

  val hasAnvilContributesTo: LookupPredicate
    get() = ClassIds.anvilContributesTo.lookupPredicateAnnotated()
  val hasAnvilContributesBinding: LookupPredicate
    get() = ClassIds.anvilContributesBinding.lookupPredicateAnnotated()
  val hasAnvilContributesMultibinding: LookupPredicate
    get() = ClassIds.anvilContributesMultibinding.lookupPredicateAnnotated()
  val hasAnvilContributesSubcomponent: LookupPredicate
    get() = ClassIds.anvilContributesSubcomponent.lookupPredicateAnnotated()

  val hasAnyAnvilContributes: LookupPredicate
    get() = hasAnvilContributesTo
      .or(hasAnvilContributesBinding)
      .or(hasAnvilContributesMultibinding)
      .or(hasAnvilContributesSubcomponent)

  val hasAnvilInternalContributedModule: LookupPredicate
    get() = ClassIds.anvilInternalContributedModule.lookupPredicateAnnotated()

  val hasComponentAnnotation: LookupPredicate
    get() = ClassIds.daggerComponent.lookupPredicateAnnotated()
  val hasModuleAnnotation: LookupPredicate
    get() = ClassIds.daggerModule.lookupPredicateAnnotated()
  val hasInjectAnnotation: LookupPredicate
    get() = ClassIds.javaxInject.lookupPredicateAnnotated()

  val contributedModule: LookupPredicate
    get() = LookupPredicate.create {
      annotated(ClassIds.anvilContributesTo.asSingleFqName()).and(annotated(ClassIds.daggerModule.asSingleFqName()))
    }

  val hasContributesSubcomponentAnnotation: LookupPredicate
    get() = ClassIds.anvilContributesSubcomponent.lookupPredicateAnnotated()
  val hasContributesToAnnotation: LookupPredicate
    get() = ClassIds.anvilContributesTo.lookupPredicateAnnotated()

  val hasMergeComponentAnnotation: LookupPredicate
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
