package com.squareup.anvil.compiler.k2.fir.contributions

import com.squareup.anvil.compiler.k2.utils.fir.requireClassId
import com.squareup.anvil.compiler.k2.utils.fir.resolveConeType
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.caches.FirLazyValue
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.expressions.FirArrayLiteral
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirGetClassCall
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.fir.expressions.unwrapArgument
import org.jetbrains.kotlin.fir.extensions.FirSupertypeGenerationExtension
import org.jetbrains.kotlin.fir.psi
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.name.ClassId

public sealed interface ScopedContribution {
  public val scopeType: ClassId
  public val contributedType: ClassId

  public val replaces: List<ClassId>
}

public sealed interface ContributedTo : ScopedContribution

/**
 * Something with both `@ContributesTo` and `@Module` annotations.
 *
 * @see com.squareup.anvil.annotations.ContributesTo
 */
public data class ContributedModule(
  override val scopeType: ClassId,
  override val contributedType: ClassId,
  override val replaces: List<ClassId>,
) : ContributedTo

/**
 * An interface or abstract class with `@ContributesTo` annotation but *not* `@Module`.
 *
 * @see com.squareup.anvil.annotations.ContributesTo
 */
public data class ContributedSupertype(
  override val scopeType: ClassId,
  override val contributedType: ClassId,
  override val replaces: List<ClassId>,
) : ContributedTo

/**
 * An interface or abstract class with `@ContributesSubcomponent` annotation.
 *
 * @see com.squareup.anvil.annotations.ContributesSubcomponent
 */
public data class ContributedSubcomponent(
  override val scopeType: ClassId,
  public val parentScopeType: ClassId,
  override val contributedType: ClassId,
  override val replaces: List<ClassId>,
  public val modules: List<ClassId>,
  public val exclude: List<ClassId>,
) : ScopedContribution

/**
 * @see com.squareup.anvil.annotations.ContributesBinding
 * @see com.squareup.anvil.annotations.ContributesMultibinding
 */
public class ContributedBinding(
  override val scopeType: ClassId,
  public val boundType: FirLazyValue<ClassId>,
  override val contributedType: ClassId,
  override val replaces: List<ClassId>,
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
}

/** A unique composite key for a contributed binding. */
public data class BindingKey(
  public val scopeType: ClassId,
  public val contributedType: ClassId,
  public val qualifier: Qualifier?,
)

public data class Qualifier(
  public val type: ClassId,
  public val value: String?,
) {

  public companion object {
    public fun fromAnnotation(
      firAnnotation: FirAnnotationCall,
      session: FirSession,
      resolveService: FirSupertypeGenerationExtension.TypeResolveService,
    ): Qualifier {
      val type = firAnnotation.requireClassId(session)

      fun FirExpression.value(): String = when (val unwrapped = unwrapArgument()) {
        is FirArrayLiteral -> unwrapped.arguments.joinToString(",") { argument ->
          argument.value()
        }

        is FirGetClassCall -> unwrapped.resolveConeType(resolveService).classId.toString()
        else -> unwrapped.psi?.text ?: unwrapped.toString()
      }

      val value = firAnnotation.argumentList.arguments.joinToString(",") { it.value() }

      return Qualifier(type = type, value = value)
    }
  }
}
