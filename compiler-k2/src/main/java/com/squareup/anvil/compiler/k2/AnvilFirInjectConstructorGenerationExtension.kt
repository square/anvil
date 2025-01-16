package com.squareup.anvil.compiler.k2

import com.squareup.anvil.compiler.k2.internal.AnvilPredicates
import com.squareup.anvil.compiler.k2.internal.DefaultGeneratedDeclarationKey
import com.squareup.anvil.compiler.k2.internal.factory
import com.squareup.anvil.compiler.k2.internal.wrapInProvider
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.extensions.ExperimentalTopLevelDeclarationsGenerationApi
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.extensions.NestedClassGenerationContext
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.plugin.ConstructorBuildingContext
import org.jetbrains.kotlin.fir.plugin.createCompanionObject
import org.jetbrains.kotlin.fir.plugin.createConstructor
import org.jetbrains.kotlin.fir.plugin.createMemberFunction
import org.jetbrains.kotlin.fir.plugin.createMemberProperty
import org.jetbrains.kotlin.fir.plugin.createTopLevelClass
import org.jetbrains.kotlin.fir.resolve.providers.getRegularClassSymbolByClassId
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.constructClassLikeType
import org.jetbrains.kotlin.fir.types.constructType
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames

/**
 * Given this kotlin source:
 * class Inject @Inject constructor(private val param0: String)
 *
 * Using a FirDeclarationGenerationExtension in kotlin k2 fir plugin generate a class file
 * representing the following:
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
@OptIn(ExperimentalTopLevelDeclarationsGenerationApi::class)
public class AnvilFirInjectConstructorGenerationExtension(session: FirSession) :
  FirDeclarationGenerationExtension(session) {
  public companion object {
    private val PREDICATE = AnvilPredicates.hasInjectAnnotation
  }

  private object Key : DefaultGeneratedDeclarationKey()

  private val predicateBasedProvider = session.predicateBasedProvider
  private val factoriesToGenerate: Map<ClassId, InjectConstructorGenerationTypes> by lazy {
    predicateBasedProvider.getSymbolsByPredicate(PREDICATE)
      .filterIsInstance<FirConstructorSymbol>()
      .associate { constructorSymbol ->
        val classIdToGenerate = constructorSymbol.callableId.classId!!.factory()
        classIdToGenerate to InjectConstructorGenerationTypes(
          toGenerateClassId = classIdToGenerate,
          matchedConstructorSymbol = constructorSymbol,
        )
      }
  }
  private val daggerFactory: FirRegularClassSymbol by lazy {
    // The following crashes
    // val factoryClassId = session.getRegularClassSymbolByClassId(Names.dagger.factory.classId())!!
    val factoryClassId = ClassId.fromString("dagger/internal/Factory")

    session.getRegularClassSymbolByClassId(factoryClassId)!!
  }
  private val factoryGetName = Name.identifier("get")

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    register(PREDICATE)
  }

  @ExperimentalTopLevelDeclarationsGenerationApi
  override fun getTopLevelClassIds(): Set<ClassId> = factoriesToGenerate.keys

  @ExperimentalTopLevelDeclarationsGenerationApi
  override fun generateTopLevelClassLikeDeclaration(classId: ClassId): FirClassLikeSymbol<*> {
    return createTopLevelClass(classId, Key) {
      superType(
        daggerFactory.constructType(
          typeArguments = arrayOf(factoriesToGenerate.getValue(classId).matchedClassConeType),
        ),
      )
    }.symbol
  }

  override fun getCallableNamesForClass(
    classSymbol: FirClassSymbol<*>,
    context: MemberGenerationContext,
  ): Set<Name> {
    if (context.owner.classId !in factoriesToGenerate.keys) return emptySet()

    return setOf(
      SpecialNames.INIT,
      // The FIR for factoryGetName works, however the IR is not implemented yet, uncommenting will crash.
      // factoryGetName,
      *factoriesToGenerate.getValue(context.owner.classId)
        .callableIdToParameters.keys.map { it.callableName }
        .toTypedArray(),
      SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT,
      // classSymbol.companionNewInstanceCallableName(),
    )
  }

  override fun generateConstructors(context: MemberGenerationContext): List<FirConstructorSymbol> {
    val classId = context.owner.classId

    val targetConstructor = factoriesToGenerate.getValue(classId).matchedConstructorSymbol

    val primaryConstructor: FirConstructor = createConstructor(
      owner = context.owner,
      key = Key,
      isPrimary = true,
      generateDelegatedNoArgConstructorCall = true,
    ) {
      val constructorContext: ConstructorBuildingContext = this@createConstructor
      targetConstructor.valueParameterSymbols.forEach { targetParameterSymbol ->
        constructorContext.valueParameter(
          name = targetParameterSymbol.name,
          type = targetParameterSymbol.resolvedReturnType.wrapInProvider(session.symbolProvider),
          isCrossinline = false,
          isNoinline = false,
          isVararg = false,
          hasDefaultValue = false,
          key = Key,
        )
      }
    }

    return listOf(primaryConstructor.symbol)
  }

  override fun generateFunctions(
    callableId: CallableId,
    context: MemberGenerationContext?
  ): List<FirNamedFunctionSymbol> {
    val owner = context?.owner ?: return emptyList()
    return when (callableId.callableName) {
      owner.companionNewInstanceCallableName() -> listOf(createCompanionNewInstanceFunction(owner))
      factoryGetName -> listOf(createFactoryGetFunction(owner))
      else -> emptyList()
    }
  }

  private fun createFactoryGetFunction(owner: FirClassSymbol<*>) = createMemberFunction(
      owner = owner,
      key = Key,
      name = factoryGetName,
      returnType = factoriesToGenerate.getValue(owner.classId)
        .matchedConstructorSymbol.resolvedReturnType,
    ) {
      visibility = Visibilities.Public
      modality = Modality.FINAL
    }.symbol

  override fun generateProperties(
    callableId: CallableId,
    context: MemberGenerationContext?
  ): List<FirPropertySymbol> {
    val owner = context?.owner ?: return emptyList()
    val callableParams = factoriesToGenerate[owner.classId]?.callableIdToParameters[callableId] ?: return emptyList()

    return listOf(
      createMemberProperty(
        owner = context.owner,
        key = Key,
        name = callableParams.name,
        returnType = callableParams.resolvedReturnType.wrapInProvider(session.symbolProvider),
      ) {
        //TODO is it possible to somehow initialize the property here as well?
      }.symbol,
    )
  }

  override fun generateNestedClassLikeDeclaration(
    owner: FirClassSymbol<*>,
    name: Name,
    context: NestedClassGenerationContext
  ): FirClassLikeSymbol<*>? {
    // Maybe this isn't needed since the only nested class like declaration is the companion object?
    if (owner.classId !in factoriesToGenerate.keys) return null

    return createCompanionObject(owner, Key).symbol
  }

  // TODO is this method in the companion object or the factory?
  private fun createCompanionNewInstanceFunction(
    owner: FirClassSymbol<*>
  ): FirNamedFunctionSymbol {
    val factoryToGenerate = factoriesToGenerate.getValue(owner.classId)
    val symbol = createMemberFunction(
      owner = owner,
      key = Key,
      name = Name.identifier("newInstance"),
      // returnType = session.builtinTypes.unitType.coneType
      returnType = factoryToGenerate.matchedClassId.constructClassLikeType(),
    ) {
      factoryToGenerate
        .matchedConstructorSymbol.valueParameterSymbols.forEach { parameterSymbol ->
          this@createMemberFunction.valueParameter(
            name = parameterSymbol.name,
            type = parameterSymbol.resolvedReturnType,
          )
        }
    }.symbol
    return symbol
  }

  private fun createCompanionCreateFunction(
    owner: FirClassSymbol<*>
  ): FirNamedFunctionSymbol {
    val symbol = createMemberFunction(
      owner = owner,
      key = Key,
      name = Name.identifier("newInstance"),
      returnType = owner.constructType(),
    ) {
      factoriesToGenerate.getValue(owner.classId)
        .matchedConstructorSymbol.valueParameterSymbols.forEach { parameterSymbol ->
          this@createMemberFunction.valueParameter(
            name = parameterSymbol.name,
            type = parameterSymbol.resolvedReturnType.wrapInProvider(session.symbolProvider),
          )
        }
    }.symbol
    return symbol
  }
}

private class InjectConstructorGenerationTypes(
  val toGenerateClassId: ClassId,
  val matchedConstructorSymbol: FirConstructorSymbol,
) {
  val matchedClassId: ClassId by lazy { matchedConstructorSymbol.callableId.classId!! }
  val matchedClassConeType: ConeClassLikeType by lazy { matchedClassId.constructClassLikeType() }
  val callableIdToParameters: Map<CallableId, FirValueParameterSymbol> by lazy {
    matchedConstructorSymbol.valueParameterSymbols.associate { it.callableId() to it }
  }

  private fun FirValueParameterSymbol.callableId(): CallableId {
    return CallableId(
      classId = toGenerateClassId,
      callableName = Name.identifier("get${name.asString().capitalize()}"),
    )
  }
}

private fun FirClassSymbol<*>.companionNewInstanceCallableName(): Name =
  Name.identifier("${name.asString()}/${SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT}/newInstance")
