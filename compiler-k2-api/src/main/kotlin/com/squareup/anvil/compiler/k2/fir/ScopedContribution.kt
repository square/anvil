package com.squareup.anvil.compiler.k2.fir

import org.jetbrains.kotlin.fir.caches.FirLazyValue
import org.jetbrains.kotlin.fir.declarations.FirClassLikeDeclaration
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name

public sealed interface ScopedMerge {
  public val scopeType: FirLazyValue<ClassId>
  public val targetType: ClassId
  public val containingDeclaration: FirLazyValue<FirClassLikeDeclaration>

  public val exclude: FirLazyValue<List<ClassId>>
}

public class MergedComponent(
  override val scopeType: FirLazyValue<ClassId>,
  override val targetType: ClassId,
  public val modules: FirLazyValue<List<ClassId>>,
  public val dependencies: FirLazyValue<List<ClassId>>,
  override val exclude: FirLazyValue<List<ClassId>>,
  override val containingDeclaration: FirLazyValue<FirClassLikeDeclaration>,
  public val mergeAnnotationCall: FirLazyValue<FirAnnotationCall>,
) : ScopedMerge

public sealed interface ScopedContribution {
  public val scopeType: FirLazyValue<ClassId>
  public val contributedType: ClassId

  public val replaces: FirLazyValue<List<ClassId>>
}

public sealed interface ContributedTo : ScopedContribution

/**
 * Something with both `@ContributesTo` and `@Module` annotations.
 *
 * @see com.squareup.anvil.annotations.ContributesTo
 */
public class ContributedModule(
  override val scopeType: FirLazyValue<ClassId>,
  override val contributedType: ClassId,
  override val replaces: FirLazyValue<List<ClassId>>,
) : ContributedTo

/**
 * An interface or abstract class with `@ContributesTo` annotation but *not* `@Module`.
 *
 * @see com.squareup.anvil.annotations.ContributesTo
 */
public class ContributedSupertype(
  override val scopeType: FirLazyValue<ClassId>,
  override val contributedType: ClassId,
  override val replaces: FirLazyValue<List<ClassId>>,
) : ContributedTo

/**
 * An interface or abstract class with `@ContributesSubcomponent` annotation.
 *
 * @see com.squareup.anvil.annotations.ContributesSubcomponent
 */
public class ContributedSubcomponent(
  override val scopeType: FirLazyValue<ClassId>,
  public val parentScopeType: ClassId,
  override val contributedType: ClassId,
  override val replaces: FirLazyValue<List<ClassId>>,
  public val modules: List<ClassId>,
  public val exclude: List<ClassId>,
) : ScopedContribution

/**
 * @see com.squareup.anvil.annotations.ContributesBinding
 * @see com.squareup.anvil.annotations.ContributesMultibinding
 */
public class ContributedBinding(
  override val scopeType: FirLazyValue<ClassId>,
  public val boundType: FirLazyValue<ClassId>,
  override val contributedType: ClassId,
  override val replaces: FirLazyValue<List<ClassId>>,
  public val rank: Int,
  public val ignoreQualifier: Boolean,
  public val isMultibinding: Boolean,
  public val bindingModule: ClassId,
  // val bindingModuleSymbol: FirClassSymbol<*>,
  public val qualifier: Qualifier?,
) : ScopedContribution {
  public val bindingKey: BindingKey by lazy(LazyThreadSafetyMode.NONE) {
    BindingKey(
      scopeType = scopeType,
      contributedType = contributedType,
      qualifier = qualifier,
    )
  }
  public val bindingCallableName: Name by lazy(LazyThreadSafetyMode.NONE) {
    val relativeName = boundType.getValue().relativeClassName.asString().replace('.', '_')
    Name.identifier("bind_$relativeName")
  }
}

/** A unique composite key for a contributed binding. */
public class BindingKey(
  public val scopeType: FirLazyValue<ClassId>,
  public val contributedType: ClassId,
  public val qualifier: Qualifier?,
)

public class Qualifier(
  public val type: ClassId,
  public val value: String?,
) {

  public companion object
}
