package com.squareup.anvil.compiler.k2.fir.contributions

import com.squareup.anvil.compiler.k2.fir.internal.requireClassId
import com.squareup.anvil.compiler.k2.fir.internal.resolveConeType
import com.squareup.anvil.compiler.k2.fir.internal.tree.FirTreePrinter.Companion.printEverything
import com.squareup.anvil.compiler.k2.utils.names.FqNames
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.FirFunctionTarget
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.caches.FirLazyValue
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirPropertyBodyResolveState
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.declarations.builder.buildProperty
import org.jetbrains.kotlin.fir.declarations.builder.buildPropertyAccessor
import org.jetbrains.kotlin.fir.declarations.impl.FirDefaultPropertyBackingField
import org.jetbrains.kotlin.fir.declarations.impl.FirResolvedDeclarationStatusImpl
import org.jetbrains.kotlin.fir.declarations.origin
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.expressions.FirArrayLiteral
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirGetClassCall
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.fir.expressions.builder.buildLiteralExpression
import org.jetbrains.kotlin.fir.expressions.builder.buildReturnExpression
import org.jetbrains.kotlin.fir.expressions.impl.buildSingleExpressionBlock
import org.jetbrains.kotlin.fir.expressions.unwrapArgument
import org.jetbrains.kotlin.fir.extensions.ExperimentalTopLevelDeclarationsGenerationApi
import org.jetbrains.kotlin.fir.extensions.FirExtension
import org.jetbrains.kotlin.fir.extensions.FirSupertypeGenerationExtension
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.plugin.createTopLevelProperty
import org.jetbrains.kotlin.fir.psi
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertyAccessorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.toEffectiveVisibility
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.utils.exceptions.withFirEntry
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.ConstantValueKind
import org.jetbrains.kotlin.utils.exceptions.errorWithAttachment
import java.security.MessageDigest

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
) : ContributedTo {

  val moduleHash: String by lazy {
    MessageDigest.getInstance("MD5").apply {
      // The contributed type name must be unique, so its hash should be unique,
      // but it can be contributed multiple times to different scopes.
      update(scopeType.asString().toByteArray())
      update(contributedType.asString().toByteArray())
    }
      .digest()
      .take(8)
      .joinToString("") { String.format("%02x", it) }
  }
  val hintPropertyName: Name by lazy {
    Name.identifier(
      contributedType.asFqNameString()
        .replace('.', '_')
        .plus("_$moduleHash"),
    )
  }

  val hintPropertyCallableId: CallableId by lazy {
    CallableId(FqNames.anvilHintPackage, hintPropertyName)
  }

  val hintFileName: String by lazy {
    contributedType.asFqNameString()
      .replace('.', '_')
      .take(243)
      .plus("_$moduleHash.kt")
  }

  @Suppress("ktlint:standard:backing-property-naming")
  private var _hintPropertyHolder: FirProperty? = null

  @OptIn(ExperimentalTopLevelDeclarationsGenerationApi::class)
  public fun hintProperty(firExtension: FirExtension): FirProperty = synchronized(this) {

    val session = firExtension.session

    return _hintPropertyHolder ?: buildProperty {

      resolvePhase = FirResolvePhase.BODY_RESOLVE
      moduleData = session.moduleData
      origin = GeneratedBindingHintKey.origin

      source = null

      symbol = FirPropertySymbol(hintPropertyCallableId)
      name = hintPropertyCallableId.callableName

      val resolvedStatus = FirResolvedDeclarationStatusImpl(
        visibility = Visibilities.Private,
        modality = Modality.FINAL,
        effectiveVisibility = Visibilities.Private.toEffectiveVisibility(
          ownerSymbol = null,
          forClass = false,
          checkPublishedApi = false,
        ),
      )
      status = resolvedStatus

      dispatchReceiverType = null
      returnTypeRef = session.builtinTypes.stringType
      isVar = false
      isLocal = false
      val getterTarget = FirFunctionTarget(labelName = null, isLambda = false)
      val getterAccessor = buildPropertyAccessor {
        status = resolvedStatus.apply { isInline = true }
        symbol = FirPropertyAccessorSymbol()
        moduleData = firExtension.session.moduleData
        origin = GeneratedBindingHintKey.origin
        returnTypeRef = firExtension.session.builtinTypes.stringType
        propertySymbol = this@buildProperty.symbol
        isGetter = true
        body = buildSingleExpressionBlock(
          buildReturnExpression {
            result = buildLiteralExpression(
              source = null,
              kind = ConstantValueKind.String,
              value = "boo",
              annotations = null,
              setType = true,
              prefix = null,
            )
            target = getterTarget

            source = null
          },
        )
      }
      getter = getterAccessor
      getterTarget.bind(getterAccessor)

      backingField = FirDefaultPropertyBackingField(
        moduleData = session.moduleData,
        origin = GeneratedBindingHintKey.origin,
        source = null,
        annotations = mutableListOf(),
        returnTypeRef = returnTypeRef,
        isVar = isVar,
        propertySymbol = symbol,
        status = status,
        resolvePhase = FirResolvePhase.BODY_RESOLVE,
      )
      bodyResolveState = FirPropertyBodyResolveState.ALL_BODIES_RESOLVED
    }
      .also { firProperty ->

        // firExtension.session.createSyntheticFile(
        //   origin = GeneratedBindingHintKey.origin,
        //   packageName = FqNames.anvilHintPackage,
        //   simpleName = hintPropertyName.asString() + ".kt",
        //   declarations = listOf(firProperty),
        // )

        firProperty.printEverything()

        _hintPropertyHolder = firProperty
      }
      ?: firExtension.createTopLevelProperty(
        key = GeneratedBindingHintKey,
        callableId = hintPropertyCallableId,
        returnType = firExtension.session.builtinTypes.stringType.coneType,
        isVal = true,
        hasBackingField = false,
      ) {
        visibility = Visibilities.Private
        status {
          isInline = true
        }
      }
        .apply {
          val g = getter ?: errorWithAttachment("generated property getter is null") {
            withFirEntry("property", this@apply)
          }

          // replaceGetter(
          //   buildPropertyAccessor {
          //     status = FirResolvedDeclarationStatusImpl(
          //       visibility = Visibilities.Private,
          //       modality = Modality.FINAL,
          //       effectiveVisibility = EffectiveVisibility.PrivateInFile,
          //     ).apply { isInline = true }
          //     symbol = FirPropertyAccessorSymbol()
          //     moduleData = firExtension.session.moduleData
          //     origin = GeneratedBindingHintKey.origin
          //     returnTypeRef = firExtension.session.builtinTypes.stringType
          //     propertySymbol = this@apply.symbol
          //     isGetter = true
          //     body = buildSingleExpressionBlock(
          //       buildReturnExpression {
          //         result = buildLiteralExpression(
          //           source = initializer?.source?.fakeElement(KtFakeSourceElementKind.PluginGenerated),
          //           kind = ConstantValueKind.String,
          //           value = "boo",
          //           annotations = null,
          //           setType = true,
          //           prefix = null,
          //         )
          //         target = FirFunctionTarget(labelName = null, isLambda = false)
          //         source = null
          //       },
          //     )
          //   },
          // )

          g

          // replaceGetter(
          //   buildPropertyAccessorCopy(getter!!) {
          //     body = buildSingleExpressionBlock(
          //       buildReturnExpression {
          //         result = buildLiteralExpression(
          //           source = initializer?.source?.fakeElement(KtFakeSourceElementKind.PluginGenerated),
          //           kind = ConstantValueKind.String,
          //           value = "boo",
          //           annotations = null,
          //           setType = true,
          //           prefix = null,
          //         )
          //       },
          //     )
          //   },
          // )
        }
        .also { firProperty ->

          // firExtension.session.createSyntheticFile(
          //   origin = GeneratedBindingHintKey.origin,
          //   packageName = FqNames.anvilHintPackage,
          //   simpleName = hintPropertyName.asString() + ".kt",
          //   declarations = listOf(firProperty),
          // )

          // firProperty.printEverything()

          _hintPropertyHolder = firProperty
        }
  }
}

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
  val parentScopeType: ClassId,
  override val contributedType: ClassId,
  override val replaces: List<ClassId>,
  val modules: List<ClassId>,
  val exclude: List<ClassId>,
) : ScopedContribution

/**
 * @see com.squareup.anvil.annotations.ContributesBinding
 * @see com.squareup.anvil.annotations.ContributesMultibinding
 */
public data class ContributedBinding(
  override val scopeType: ClassId,
  val boundType: FirLazyValue<ClassId>,
  override val contributedType: ClassId,
  override val replaces: List<ClassId>,
  val rank: Int,
  val ignoreQualifier: Boolean,
  val isMultibinding: Boolean,
  val bindingModule: ClassId,
  // val bindingModuleSymbol: FirClassSymbol<*>,
  val qualifier: Qualifier?,
) : ScopedContribution {
  val bindingKey: BindingKey by lazy(LazyThreadSafetyMode.NONE) {
    BindingKey(
      scopeType = scopeType,
      contributedType = contributedType,
      qualifier = qualifier,
    )
  }
}

/** A unique composite key for a contributed binding. */
public data class BindingKey(
  val scopeType: ClassId,
  val contributedType: ClassId,
  val qualifier: Qualifier?,
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
