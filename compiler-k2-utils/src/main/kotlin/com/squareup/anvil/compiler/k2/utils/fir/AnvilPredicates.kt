package com.squareup.anvil.compiler.k2.utils.fir

import com.squareup.anvil.compiler.k2.utils.names.ClassIds
import org.jetbrains.kotlin.fir.extensions.predicate.DeclarationPredicate
import org.jetbrains.kotlin.fir.extensions.predicate.LookupPredicate
import org.jetbrains.kotlin.fir.extensions.predicate.LookupPredicate.BuilderContext.or
import org.jetbrains.kotlin.name.ClassId

public object AnvilPredicates {

  /*
  Anvil Contributes annotations
   */
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
  public val hasAnyAnvilContributes: LookupPredicate
    get() = hasAnvilContributesTo
      .or(hasAnvilContributesBinding)
      .or(hasAnvilContributesMultibinding)
      .or(hasAnvilContributesSubcomponent)

  /*
  Anvil hint annotations
   */
  public val hasAnvilInternalContributedModuleHints: LookupPredicate
    get() = ClassIds.anvilInternalContributedModuleHints.lookupPredicateAnnotated()
  public val hasAnvilInternalContributedComponentHints: LookupPredicate
    get() = ClassIds.anvilInternalContributedComponentHints.lookupPredicateAnnotated()

  /*
  Anvil Merge annotations
   */
  public val hasAnvilMergeComponent: LookupPredicate
    get() = ClassIds.anvilMergeComponent.lookupPredicateAnnotated()
  public val hasAnvilMergeSubcomponent: LookupPredicate
    get() = ClassIds.anvilMergeSubcomponent.lookupPredicateAnnotated()
  public val hasAnvilMergeModules: LookupPredicate
    get() = ClassIds.anvilMergeModules.lookupPredicateAnnotated()
  public val hasAnvilMergeInterfaces: LookupPredicate
    get() = ClassIds.anvilMergeInterfaces.lookupPredicateAnnotated()
  public val hasAnyAnvilMerge: LookupPredicate
    get() = hasAnvilMergeComponent
      .or(hasAnvilMergeSubcomponent)
      .or(hasAnvilMergeModules)
      .or(hasAnvilMergeInterfaces)

  /*
   Dagger annotations
   */
  public val hasDaggerModule: LookupPredicate
    get() = ClassIds.daggerModule.lookupPredicateAnnotated()
  public val hasDaggerBinds: LookupPredicate
    get() = ClassIds.daggerBinds.lookupPredicateAnnotated()
  public val hasDaggerComponent: LookupPredicate
    get() = ClassIds.daggerComponent.lookupPredicateAnnotated()
  public val hasDaggerSubcomponent: LookupPredicate
    get() = ClassIds.daggerSubcomponent.lookupPredicateAnnotated()
  public val hasDaggerProvides: LookupPredicate
    get() = ClassIds.daggerProvides.lookupPredicateAnnotated()

  public val hasAnyDaggerAnnotation: LookupPredicate
    get() = hasDaggerModule
      .or(hasDaggerBinds)
      .or(hasDaggerComponent)
      .or(hasDaggerSubcomponent)
      .or(hasDaggerProvides)

  /*
   JSR-330 annotations
   */
  public val hasInjectAnnotation: LookupPredicate
    get() = ClassIds.javaxInject.lookupPredicateAnnotated()

  public val hasQualifierMetaAnnotation: DeclarationPredicate
    get() = DeclarationPredicate.create {
      metaAnnotated(
        ClassIds.javaxQualifier.asSingleFqName(),
        includeItself = true,
      )
    }

  private fun ClassId.lookupPredicateAnnotated(): LookupPredicate =
    LookupPredicate.create {
      annotated(this@lookupPredicateAnnotated.asSingleFqName())
    }

  private fun ClassId.declarationPredicateAnnotated(): DeclarationPredicate =
    DeclarationPredicate.create { annotated(this@declarationPredicateAnnotated.asSingleFqName()) }
}
