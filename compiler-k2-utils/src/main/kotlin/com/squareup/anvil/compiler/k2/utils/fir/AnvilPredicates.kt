package com.squareup.anvil.compiler.k2.utils.fir

import com.squareup.anvil.compiler.k2.utils.names.ClassIds
import org.jetbrains.kotlin.fir.extensions.predicate.DeclarationPredicate
import org.jetbrains.kotlin.fir.extensions.predicate.LookupPredicate
import org.jetbrains.kotlin.fir.extensions.predicate.LookupPredicate.BuilderContext.or
import org.jetbrains.kotlin.name.ClassId

public object AnvilPredicates {

  public val contributedModule: LookupPredicate
    get() = LookupPredicate.create {
      annotated(ClassIds.anvilContributesTo.asSingleFqName())
        .and(annotated(ClassIds.daggerModule.asSingleFqName()))
    }
  public val hasAnvilContributesBinding: LookupPredicate
    get() = ClassIds.anvilContributesBinding.lookupPredicateAnnotated()
  public val hasAnvilContributesMultibinding: LookupPredicate
    get() = ClassIds.anvilContributesMultibinding.lookupPredicateAnnotated()
  public val hasAnvilContributesSubcomponent: LookupPredicate
    get() = ClassIds.anvilContributesSubcomponent.lookupPredicateAnnotated()
  public val hasAnvilContributesTo: LookupPredicate
    get() = ClassIds.anvilContributesTo.lookupPredicateAnnotated()
  public val hasAnvilInternalContributedModule: LookupPredicate
    get() = ClassIds.anvilInternalContributedModule.lookupPredicateAnnotated()
  public val hasAnyAnvilContributes: LookupPredicate
    get() = hasAnvilContributesTo
      .or(hasAnvilContributesBinding)
      .or(hasAnvilContributesMultibinding)
      .or(hasAnvilContributesSubcomponent)
  public val hasInjectAnnotation: LookupPredicate
    get() = ClassIds.javaxInject.lookupPredicateAnnotated()
  public val hasMergeComponentAnnotation: LookupPredicate
    get() = ClassIds.anvilMergeComponent.lookupPredicateAnnotated()
  public val hasModuleAnnotation: LookupPredicate
    get() = ClassIds.daggerModule.lookupPredicateAnnotated()

  public val hasQualifierMetaAnnotation: DeclarationPredicate
    get() = DeclarationPredicate.create {
      metaAnnotated(
        ClassIds.javaxQualifier.asSingleFqName(),
        includeItself = true,
      )
    }

  private fun ClassId.lookupPredicateAnnotated(): LookupPredicate =
    LookupPredicate.create { annotated(this@lookupPredicateAnnotated.asSingleFqName()) }
}
