package com.squareup.anvil.compiler.k2

import com.squareup.anvil.compiler.k2.internal.AnvilPredicates
import com.squareup.anvil.compiler.k2.internal.DefaultGeneratedDeclarationKey
import com.squareup.anvil.compiler.k2.internal.factory
import com.squareup.anvil.compiler.k2.internal.wrapInProvider
import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.fakeElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.expressions.builder.buildPropertyAccessExpression
import org.jetbrains.kotlin.fir.extensions.ExperimentalTopLevelDeclarationsGenerationApi
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.plugin.createConstructor
import org.jetbrains.kotlin.fir.plugin.createMemberProperty
import org.jetbrains.kotlin.fir.plugin.createTopLevelClass
import org.jetbrains.kotlin.fir.references.builder.buildPropertyFromParameterResolvedNamedReference
import org.jetbrains.kotlin.fir.references.builder.buildResolvedNamedReference
import org.jetbrains.kotlin.fir.references.builder.buildSimpleNamedReference
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames

private typealias FactoryClassId = ClassId
private typealias TargetClassId = ClassId

private data class TTTT(
  val factoryClassId: FactoryClassId,
  val targetClassId: TargetClassId,
  val factoryConstructor: FirConstructor,
  val targetConstructor: FirConstructorSymbol,
) {
  data class FactoryParam(
    val name: Name,
    val type: ConeClassLikeType,
  )
}

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

  internal object Key : DefaultGeneratedDeclarationKey()

  private val predicateBasedProvider = session.predicateBasedProvider
  private val matchedClasses by lazy {
    predicateBasedProvider.getSymbolsByPredicate(PREDICATE)
      .filterIsInstance<FirConstructorSymbol>()
  }

  private val factoryClassIdToTargetConstructor by lazy {
    matchedClasses.associateBy { it.callableId.classId!!.factory() }
  }

  private val butt by lazy {
    matchedClasses.associate { targetConstructor ->
      val targetId = targetConstructor.callableId.classId!!
      val factoryId = targetId.factory()
      val params = targetConstructor.valueParameterSymbols.map { param ->
        TTTT.FactoryParam(param.name, param.resolvedReturnType.wrapInProvider(session.symbolProvider))
      }
      val factoryConstructor = createConstructor(
        owner = factoryId,
        key = Key,
        isPrimary = true,
        generateDelegatedNoArgConstructorCall = true,
      ) {
        params.forEach { param ->
          valueParameter(
            name = param.name,
            type = param.type,
          )
        }
      }
      factoryId to TTTT(
        factoryClassId = factoryId,
        targetClassId = targetId,
        factoryConstructor = factoryConstructor,
        targetConstructor = targetConstructor,
      )
    }
  }

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    register(PREDICATE)
  }

  @ExperimentalTopLevelDeclarationsGenerationApi
  override fun getTopLevelClassIds(): Set<ClassId> = factoryClassIdToTargetConstructor.keys

  @ExperimentalTopLevelDeclarationsGenerationApi
  override fun generateTopLevelClassLikeDeclaration(classId: ClassId): FirClassLikeSymbol<*> {
    return createTopLevelClass(classId, Key).symbol
    // return createTopLevelClass(classId.factory(), Key).symbol
  }

  override fun getCallableNamesForClass(
    classSymbol: FirClassSymbol<*>,
    context: MemberGenerationContext,
  ): Set<Name> {
    val targetConstructor =
      factoryClassIdToTargetConstructor[classSymbol.classId] ?: return emptySet()
    return setOf(SpecialNames.INIT) + targetConstructor.valueParameterSymbols.map { it.name }
  }

  override fun generateProperties(
    callableId: CallableId,
    context: MemberGenerationContext?,
  ): List<FirPropertySymbol> {

    val factoryClassSymbol = context!!.owner as FirRegularClassSymbol
    val classId = factoryClassSymbol.classId

    val targetConstructor = factoryClassIdToTargetConstructor.getValue(classId)

    return targetConstructor.valueParameterSymbols.map { param ->
      createMemberProperty(
        owner = context.owner,
        key = Key,
        name = param.name,
        returnType = param.resolvedReturnType.wrapInProvider(session.symbolProvider),
        isVal = true,
      )
        .apply {

          replaceInitializer(
            buildPropertyAccessExpression {

              calleeReference = buildResolvedNamedReference {
              }

              buildPropertyFromParameterResolvedNamedReference { }

              calleeReference = buildSimpleNamedReference {
                name = param.name
                source = param.resolvedInitializer?.source
                  ?.fakeElement(KtFakeSourceElementKind.PluginGenerated)
              }
            },
          )

          // transformInitializer(
          //   object : FirDefaultTransformer<Nothing?>() {
          //     override fun <E : FirElement> transformElement(element: E, data: Nothing?): E {
          //
          //       @Suppress("UNCHECKED_CAST")
          //       return buildPropertyAccessExpression {
          //         // source = param.source?.fakeElement(KtFakeSourceElementKind.PluginGenerated)
          //
          //         // calleeReference = buildResolvedNamedReference {
          //         // }
          //
          //         val resolvedCalleeReference = buildPropertyFromParameterResolvedNamedReference {
          //           name = param.name
          //
          //           val factoryConstructor = factoryClassSymbol.declarationSymbols
          //             .firstOrNull { it is FirConstructorSymbol } as? FirConstructorSymbol
          //
          //           requireNotNull(factoryConstructor)
          //
          //           resolvedSymbol =
          //             factoryConstructor.valueParameterSymbols.first { it.name == param.name }
          //         }
          //
          //         // calleeReference = buildSimpleNamedReference {
          //         //   name = param.name
          //         //   // source =
          //         //   //   param.resolvedInitializer?.source?.fakeElement(KtFakeSourceElementKind.PluginGenerated)
          //         // }
          //       } as E
          //     }
          //   },
          //   null,
          // )
        }
        .symbol
    }
  }

  override fun generateConstructors(context: MemberGenerationContext): List<FirConstructorSymbol> {

    val classId = context.owner.classId

    val targetConstructor = factoryClassIdToTargetConstructor.getValue(classId)

    val targetParams = targetConstructor.valueParameterSymbols

    return listOf(
      createConstructor(
        owner = context.owner,
        key = Key,
        isPrimary = true,
        generateDelegatedNoArgConstructorCall = true,
      ) {
        targetParams.forEach { param ->
          valueParameter(
            name = param.name,
            type = param.resolvedReturnType.wrapInProvider(session.symbolProvider),
          )
        }
      }
        .symbol,
    )
  }
}
