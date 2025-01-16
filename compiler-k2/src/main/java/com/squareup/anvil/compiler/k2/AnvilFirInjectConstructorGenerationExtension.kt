package com.squareup.anvil.compiler.k2

import com.squareup.anvil.compiler.k2.internal.AnvilPredicates
import com.squareup.anvil.compiler.k2.internal.DefaultGeneratedDeclarationKey
import com.squareup.anvil.compiler.k2.internal.factory
import com.squareup.anvil.compiler.k2.internal.wrapInProvider
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.ExperimentalTopLevelDeclarationsGenerationApi
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.plugin.createConstructor
import org.jetbrains.kotlin.fir.plugin.createMemberProperty
import org.jetbrains.kotlin.fir.plugin.createTopLevelClass
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames

/**
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

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    register(PREDICATE)
  }

  private val toGenerate by lazy {
    matchedClasses.associateBy { it.callableId.classId!!.factory() }
  }

  @ExperimentalTopLevelDeclarationsGenerationApi
  override fun getTopLevelClassIds(): Set<ClassId> = toGenerate.keys

  @ExperimentalTopLevelDeclarationsGenerationApi
  override fun generateTopLevelClassLikeDeclaration(classId: ClassId): FirClassLikeSymbol<*> {
    return createTopLevelClass(classId, Key).symbol
    // return createTopLevelClass(classId.factory(), Key).symbol
  }

  override fun getCallableNamesForClass(
    classSymbol: FirClassSymbol<*>,
    context: MemberGenerationContext,
  ): Set<Name> = if (context.owner.classId in toGenerate) {
    setOf(SpecialNames.INIT, Name.identifier("joel"))
  } else {
    emptySet()
  }

  override fun generateProperties(
    callableId: CallableId,
    context: MemberGenerationContext?,
  ): List<FirPropertySymbol> {

    val classId = context!!.owner.classId

    val targetConstructor = toGenerate.getValue(classId)

    return targetConstructor.valueParameterSymbols.map { param ->
      createMemberProperty(
        owner = context.owner,
        key = Key,
        name = param.name,
        returnType = param.resolvedReturnType.wrapInProvider(session.symbolProvider),
        isVal = true,
      ).symbol
    }
  }

  override fun generateConstructors(context: MemberGenerationContext): List<FirConstructorSymbol> {

    val classId = context.owner.classId

    val targetConstructor = toGenerate.getValue(classId)

    val targetParams = targetConstructor.valueParameterSymbols
      .onEach { param ->

        val type2 = param.resolvedReturnType.wrapInProvider(session.symbolProvider)

        println("name: ${param.name}  -- type: ${param.resolvedReturnType.classId}  --  type2: $type2")
      }

    // buildPrimaryConstructor {
    //   val b = this@buildPrimaryConstructor
    //   b.valueParameters += targetParams.map { param ->
    //
    //     buildDefaultSetterValueParameter {
    //     }
    //
    //     val vp = buildValueParameter {
    //       val c = this@buildValueParameter
    //       c.name = param.name
    //       c.backingField
    //     }
    //   }
    // }
    return listOf(
      createConstructor(
        owner = context.owner,
        key = Key,
        isPrimary = true,
        generateDelegatedNoArgConstructorCall = true,
      ) {
        targetParams.forEach { param ->
          valueParameter(
            param.name,
            param.resolvedReturnType.wrapInProvider(session.symbolProvider),
          )
          // buildValueParameter {
          //   moduleData = session.moduleData
          //   origin = Key.origin
          //
          //   name = param.name
          //   symbol = param
          //   returnTypeRef = param.resolvedReturnType.wrapInProvider(session.symbolProvider)
          //     .toFirResolvedTypeRef()
          //   visibility = Visibilities.Public
          // }
        }
      }
        .apply {
          // replaceAnnotations(
          //   listOf(
          //     buildAnnotation {
          //       annotationTypeRef = Names.inject.createUserType(null, false)
          //       argumentMapping = FirEmptyAnnotationArgumentMapping
          //     },
          //   ),
          // )
        }
        .symbol,
    )
  }
}
