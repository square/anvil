package com.squareup.anvil.compiler.k2

import com.squareup.anvil.compiler.k2.internal.AnvilPredicates
import com.squareup.anvil.compiler.k2.internal.DefaultGeneratedDeclarationKey
import com.squareup.anvil.compiler.k2.internal.factory
import com.squareup.anvil.compiler.k2.internal.wrapInProvider
import org.jetbrains.kotlin.descriptors.EffectiveVisibility
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.builder.buildPrimaryConstructor
import org.jetbrains.kotlin.fir.declarations.builder.buildValueParameter
import org.jetbrains.kotlin.fir.declarations.impl.FirResolvedDeclarationStatusImpl
import org.jetbrains.kotlin.fir.declarations.origin
import org.jetbrains.kotlin.fir.extensions.ExperimentalTopLevelDeclarationsGenerationApi
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.plugin.createTopLevelClass
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.toFirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.constructType
import org.jetbrains.kotlin.fir.types.impl.FirImplicitTypeRefImplWithoutSource
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames

/**
 * Hello we're trying to generate a constructor which looks like:
 *
 * ```
 * public class InjectClass_Factory(
 *   private val param0: Provider<Inner<String>>
 * ) : Factory<InjectClass> {
 *   public override fun `get`(): InjectClass = newInstance(param0.get())
 *
 *   public companion object {
 *     @JvmStatic
 *     public fun create(param0: Provider<Inner<String>>): InjectClass_Factory =
 *         InjectClass_Factory(param0)
 *
 *     @JvmStatic
 *     public fun newInstance(param0: Inner<String>): InjectClass = InjectClass(param0)
 *   }
 * }
 * ```
 */
public class AnvilFirInjectConstructorGenerationExtension(session: FirSession) :
  FirDeclarationGenerationExtension(session) {
  public companion object {
    private val PREDICATE = AnvilPredicates.hasInjectAnnotation
  }

  private object Key : DefaultGeneratedDeclarationKey()

  private val predicateBasedProvider = session.predicateBasedProvider
  private val matchedClasses by lazy {
    predicateBasedProvider.getSymbolsByPredicate(PREDICATE)
      .filterIsInstance<FirConstructorSymbol>()
  }
  private val toGenerate: Map<ClassId, FirConstructorSymbol> by lazy {
    matchedClasses.associateBy { it.callableId.classId!!.factory() }
  }

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    register(PREDICATE)
  }

  @ExperimentalTopLevelDeclarationsGenerationApi
  override fun getTopLevelClassIds(): Set<ClassId> = toGenerate.keys

  @ExperimentalTopLevelDeclarationsGenerationApi
  override fun generateTopLevelClassLikeDeclaration(classId: ClassId): FirClassLikeSymbol<*> {
    return createTopLevelClass(classId, Key).symbol
  }

  override fun getCallableNamesForClass(
    classSymbol: FirClassSymbol<*>,
    context: MemberGenerationContext,
  ): Set<Name> = if (context.owner.classId in toGenerate) {
    setOf(SpecialNames.INIT)
  } else {
    emptySet()
  }

  override fun generateConstructors(context: MemberGenerationContext): List<FirConstructorSymbol> {
    val classId = context.owner.classId
    val targetConstructor = toGenerate.getValue(classId)

    return buildPrimaryConstructor {
      origin = Key.origin
      moduleData = session.moduleData
      symbol = FirConstructorSymbol(classId)
      status = FirResolvedDeclarationStatusImpl(
        visibility = Visibilities.Public,
        modality = Modality.FINAL,
        effectiveVisibility = EffectiveVisibility.Public,
      )
      returnTypeRef = FirImplicitTypeRefImplWithoutSource
      dispatchReceiverType = FirRegularClassSymbol(classId).constructType()

      valueParameters.addAll(
        targetConstructor.valueParameterSymbols.map { param ->
          val providerType = param.resolvedReturnType
            .wrapInProvider(session.symbolProvider)
          buildValueParameter {
            name = param.name
            symbol = param
            origin = Key.origin
            moduleData = session.moduleData
            returnTypeRef = providerType.toFirResolvedTypeRef()
            containingFunctionSymbol = this@buildPrimaryConstructor.symbol
            isCrossinline = false
            isNoinline = false
            isVararg = false
          }
        },
      )
    }.symbol.let(::listOf)
  }
}
