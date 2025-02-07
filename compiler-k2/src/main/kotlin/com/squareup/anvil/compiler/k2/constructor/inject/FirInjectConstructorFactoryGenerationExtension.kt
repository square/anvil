package com.squareup.anvil.compiler.k2.constructor.inject

import com.squareup.anvil.compiler.k2.fir.AnvilFirContext
import com.squareup.anvil.compiler.k2.fir.AnvilFirDeclarationGenerationExtension
import com.squareup.anvil.compiler.k2.names.Names
import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.utils.isCompanion
import org.jetbrains.kotlin.fir.expressions.builder.buildPropertyAccessExpression
import org.jetbrains.kotlin.fir.extensions.ExperimentalTopLevelDeclarationsGenerationApi
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.extensions.NestedClassGenerationContext
import org.jetbrains.kotlin.fir.extensions.predicate.LookupPredicate
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.plugin.createMemberProperty
import org.jetbrains.kotlin.fir.references.builder.buildPropertyFromParameterResolvedNamedReference
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames
import kotlin.collections.get

/**
 * Given this kotlin source:
 * class InjectClass @Inject constructor(private val param0: String)
 *
 *
 * Using a FirDeclarationGenerationExtension in kotlin k2 fir plugin generate a class file
 * representing the following:
 *
 * public class InjectClass_Factory(
 *   private val param0: Provider<String>
 * ) : com.internal.Dagger.Factory<InjectClass> {
 *   public override fun `get`(): InjectClass = newInstance(param0.get())
 *
 *   public companion object {
 *     @JvmStatic
 *     public fun create(param0: dagger.Provider<String>): InjectClass_Factory =
 *         InjectClass_Factory(param0)
 *
 *     @JvmStatic
 *     public fun newInstance(param0: String): InjectClass = InjectClass(param0)
 *   }
 * }
 */
internal class FirInjectConstructorFactoryGenerationExtension(
  anvilFirContext: AnvilFirContext,
  session: FirSession,
) : AnvilFirDeclarationGenerationExtension(anvilFirContext, session) {

  private val factoriesToGenerate: Map<ClassId, InjectConstructorGenerationModel> by lazy {
    session.predicateBasedProvider.getSymbolsByPredicate(injectAnnotationPredicate)
      .filterIsInstance<FirConstructorSymbol>()
      .associate { constructorSymbol ->
        val model = InjectConstructorGenerationModel(this, session, constructorSymbol)
        model.generatedClassId to model
      }
  }

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    register(injectAnnotationPredicate)
  }

  @ExperimentalTopLevelDeclarationsGenerationApi
  override fun getTopLevelClassIds(): Set<ClassId> = factoriesToGenerate.keys

  @ExperimentalTopLevelDeclarationsGenerationApi
  override fun generateTopLevelClassLikeDeclaration(classId: ClassId): FirClassLikeSymbol<*> {
    return factoriesToGenerate.getValue(classId).generatedClassSymbol
  }

  override fun getCallableNamesForClass(
    classSymbol: FirClassSymbol<*>,
    context: MemberGenerationContext,
  ): Set<Name> {
    if (context.owner.isCompanion && context.owner.classId.parentClassId in factoriesToGenerate.keys) {
      return setOf(
        SpecialNames.INIT,
        InjectConstructorGenerationModel.createName,
        InjectConstructorGenerationModel.newInstance,
      )
    }
    if (context.owner.classId !in factoriesToGenerate.keys) return emptySet()

    return setOf(
      SpecialNames.INIT,
      *factoriesToGenerate.getValue(context.owner.classId)
        .generatedCallableIdToParameters
        .keys
        .map { it.callableName }
        .toTypedArray(),
      InjectConstructorGenerationModel.factoryGetName,
    )
  }

  override fun getNestedClassifiersNames(
    classSymbol: FirClassSymbol<*>,
    context: NestedClassGenerationContext,
  ): Set<Name> {
    return if (context.owner.classId in factoriesToGenerate.keys) {
      println("Creating nested classes for ${context.owner.classId}")
      setOf(SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT)
    } else {
      emptySet()
    }
  }

  override fun generateProperties(
    callableId: CallableId,
    context: MemberGenerationContext?,
  ): List<FirPropertySymbol> {
    val owner = context?.owner ?: return emptyList()
    val param = factoriesToGenerate[owner.classId]
      ?.generatedCallableIdToParameters[callableId]
      ?: return emptyList()

    return createMemberProperty(
      owner = context.owner,
      key = Key,
      name = param.name,
      returnType = param.resolvedReturnType,
    ).apply {
      // Assigns property values from the constructor arguments
      replaceInitializer(
        buildPropertyAccessExpression {
          coneTypeOrNull = param.resolvedReturnType
          calleeReference = buildPropertyFromParameterResolvedNamedReference {
            name = param.name
            resolvedSymbol = param
          }
        },
      )
    }
      .symbol
      .let(::listOf)
  }

  override fun generateConstructors(context: MemberGenerationContext): List<FirConstructorSymbol> {
    return if (context.owner.isCompanion) {
      val model = factoriesToGenerate.getValue(context.owner.classId.parentClassId!!)
      listOf(model.generatedCompanionConstructor.symbol)
    } else {
      val model = factoriesToGenerate.getValue(context.owner.classId)
      listOf(model.generatedConstructor.symbol)
    }
  }

  override fun generateFunctions(
    callableId: CallableId,
    context: MemberGenerationContext?,
  ): List<FirNamedFunctionSymbol> {
    val owner = context?.owner ?: return emptyList()

    return when (callableId.callableName) {
      InjectConstructorGenerationModel.createName -> {
        factoriesToGenerate[owner.classId.parentClassId]!!.createCompanionCreateFunction()
      }
      InjectConstructorGenerationModel.newInstance -> {
        factoriesToGenerate[owner.classId.parentClassId]!!.createCompanionNewInstanceFunction()
      }
      InjectConstructorGenerationModel.factoryGetName -> {
        factoriesToGenerate[owner.classId]!!.createFactoryGetFunction()
      }
      else -> null
    }.let(::listOfNotNull)
  }

  override fun generateNestedClassLikeDeclaration(
    owner: FirClassSymbol<*>,
    name: Name,
    context: NestedClassGenerationContext,
  ): FirClassLikeSymbol<*>? {
    if (name == SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT) {
      return factoriesToGenerate[owner.classId]?.generatedCompanionClass
    }
    return null
  }

  companion object Key : GeneratedDeclarationKey() {
    private val injectAnnotationPredicate = LookupPredicate.create {
      annotated(Names.javaxInject)
    }
  }
}
