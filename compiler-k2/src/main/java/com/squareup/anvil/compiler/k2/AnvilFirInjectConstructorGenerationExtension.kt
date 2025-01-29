package com.squareup.anvil.compiler.k2

import com.squareup.anvil.compiler.k2.internal.AnvilPredicates
import com.squareup.anvil.compiler.k2.internal.DefaultGeneratedDeclarationKey
import com.squareup.anvil.compiler.k2.internal.Names
import com.squareup.anvil.compiler.k2.internal.classId
import com.squareup.anvil.compiler.k2.internal.factory
import com.squareup.anvil.compiler.k2.internal.toFirAnnotation
import com.squareup.anvil.compiler.k2.internal.wrapInProvider
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirDeclarationStatus
import org.jetbrains.kotlin.fir.declarations.impl.FirDeclarationStatusImpl
import org.jetbrains.kotlin.fir.declarations.utils.isCompanion
import org.jetbrains.kotlin.fir.declarations.utils.isOverride
import org.jetbrains.kotlin.fir.expressions.builder.buildPropertyAccessExpression
import org.jetbrains.kotlin.fir.extensions.ExperimentalTopLevelDeclarationsGenerationApi
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.extensions.NestedClassGenerationContext
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.plugin.createCompanionObject
import org.jetbrains.kotlin.fir.plugin.createConstructor
import org.jetbrains.kotlin.fir.plugin.createDefaultPrivateConstructor
import org.jetbrains.kotlin.fir.plugin.createMemberFunction
import org.jetbrains.kotlin.fir.plugin.createMemberProperty
import org.jetbrains.kotlin.fir.plugin.createTopLevelClass
import org.jetbrains.kotlin.fir.references.builder.buildPropertyFromParameterResolvedNamedReference
import org.jetbrains.kotlin.fir.resolve.providers.getRegularClassSymbolByClassId
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.scopes.impl.overrides
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
internal class LoggingAnvilFirInjectConstructorGenerationExtension(
  session: FirSession,
) : FirDeclarationGenerationExtensionLogger(AnvilFirInjectConstructorGenerationExtension(session))

@OptIn(ExperimentalTopLevelDeclarationsGenerationApi::class)
internal class AnvilFirInjectConstructorGenerationExtension(
  session: FirSession
) : FirDeclarationGenerationExtension(session) {
  companion object {
    private val PREDICATE = AnvilPredicates.hasInjectAnnotation
  }

  internal object Key : DefaultGeneratedDeclarationKey()

  private val predicateBasedProvider = session.predicateBasedProvider

  private val factoriesToGenerate: Map<ClassId, InjectConstructorGenerationTypes> by lazy {
    predicateBasedProvider.getSymbolsByPredicate(PREDICATE)
      .filterIsInstance<FirConstructorSymbol>()
      .associate { constructorSymbol ->
        val data = InjectConstructorGenerationTypes(matchedConstructorSymbol = constructorSymbol)
        data.generatedClassId to data
      }
  }

  private val daggerFactory: FirRegularClassSymbol by lazy {
    val factoryClassId = Names.dagger.factory.classId()

    session.getRegularClassSymbolByClassId(factoryClassId)!!
  }
  private val factoryGetName = Name.identifier("get")
  private val createName = Name.identifier("create")
  private val newInstance = Name.identifier("newInstance")

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    register(PREDICATE)
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
    // TODO clean this up to something reusable for checking companion objects
    if (context.owner.isCompanion && context.owner.classId.parentClassId in factoriesToGenerate.keys) {
      return setOf(
        SpecialNames.INIT,
        createName,
        newInstance,
      )
    }
    if (context.owner.classId !in factoriesToGenerate.keys) return emptySet()

    val names = setOf(
      SpecialNames.INIT,
      *factoriesToGenerate.getValue(context.owner.classId)
        .generatedCallableIdToParameters
        .keys
        .map { it.callableName }
        .toTypedArray(),
      factoryGetName,
    )

    return names
  }

  override fun getNestedClassifiersNames(
    classSymbol: FirClassSymbol<*>,
    context: NestedClassGenerationContext
  ): Set<Name> {
    return if (context.owner.classId in factoriesToGenerate.keys) {
      println("Creating nested classes for ${context.owner.classId}")
      setOf(SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT)
    } else {
      return emptySet()
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
          val b = this@buildPropertyAccessExpression
          b.coneTypeOrNull = param.resolvedReturnType
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
      val data = factoriesToGenerate.getValue(context.owner.classId.parentClassId!!)
      listOf(data.generatedCompanionConstructor.symbol)
    } else {
      val data = factoriesToGenerate.getValue(context.owner.classId)
      listOf(data.generatedConstructor.symbol)
    }
  }

  override fun generateFunctions(
    callableId: CallableId,
    context: MemberGenerationContext?,
  ): List<FirNamedFunctionSymbol> {
    val owner = context?.owner ?: return emptyList()
    return when (callableId.callableName) {
      createName -> listOf(createCompanionCreateFunction(owner))
      newInstance -> listOf(createCompanionNewInstanceFunction(owner))
      factoryGetName -> listOf(createFactoryGetFunction(owner))
      else -> emptyList()
    }
  }

  private fun createFactoryGetFunction(owner: FirClassSymbol<*>): FirNamedFunctionSymbol {
    val data = factoriesToGenerate.getValue(owner.classId)
    val params = data.matchedConstructorSymbol.valueParameterSymbols.map { parameterSymbol ->
      parameterSymbol.name to parameterSymbol.resolvedReturnType
    }
    return createMemberFunction(
      owner = owner,
      key = Key,
      name = factoryGetName,
      returnType = data.matchedConstructorSymbol.resolvedReturnType,
    ) {
      visibility = Visibilities.Public
      modality = Modality.FINAL
      params.forEach { (name, returnType) ->
        this@createMemberFunction.valueParameter(name = name, type = returnType)
      }
    }.apply {
      // replaceStatus(
      //   FirDeclarationStatusImpl(
      //     visibility = Visibilities.Public,
      //       modality = Modality.FINAL
      //   ).apply {
      //     isOverride = true
      //   }
      // )
    }
      .symbol
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

  private fun createCompanionNewInstanceFunction(
    owner: FirClassSymbol<*>,
  ): FirNamedFunctionSymbol {
    val data = factoriesToGenerate.getValue(owner.classId.parentClassId!!)
    val params = data.matchedConstructorSymbol.valueParameterSymbols.map { parameterSymbol ->
      parameterSymbol.name to parameterSymbol.resolvedReturnType
    }
    val symbol = createMemberFunction(
      owner = owner,
      key = Key,
      name = newInstance,
      returnType = data.matchedClassId.constructClassLikeType(),
    ) {
      params.forEach { (name, returnType) ->
        this@createMemberFunction.valueParameter(name = name, type = returnType)
      }
    }.apply {
      replaceAnnotations(listOf(Names.kotlin.jvmStatic.toFirAnnotation()))
    }.symbol
    return symbol
  }

  private fun createCompanionCreateFunction(
    owner: FirClassSymbol<*>,
  ): FirNamedFunctionSymbol {
    val data = factoriesToGenerate.getValue(owner.classId.parentClassId!!)
    val params = data.generatedConstructor.symbol.valueParameterSymbols.map { parameterSymbol ->
      parameterSymbol.name to parameterSymbol.resolvedReturnType
    }
    val function = createMemberFunction(
      owner = owner,
      key = Key,
      name = createName,
      returnType = data.generatedClassSymbol.constructType(),
    ) {
      params.forEach { (name, returnType) ->
        this@createMemberFunction.valueParameter(name = name, type = returnType)
      }
    }.apply {
      replaceAnnotations(listOf(Names.kotlin.jvmStatic.toFirAnnotation()))
    }
    return function.symbol
  }

  private inner class InjectConstructorGenerationTypes(
    val matchedConstructorSymbol: FirConstructorSymbol,
  ) {
    val matchedClassId: ClassId by lazy { matchedConstructorSymbol.callableId.classId!! }
    val matchedClassConeType: ConeClassLikeType by lazy { matchedClassId.constructClassLikeType() }

    val generatedClassId: ClassId by lazy { matchedClassId.factory() }
    val generatedClassSymbol: FirClassSymbol<*> by lazy {
      createTopLevelClass(generatedClassId, Key) {
        superType(
          daggerFactory.constructType(
            typeArguments = arrayOf(matchedClassConeType),
          ),
        )
      }.symbol
    }
    val generatedConstructor: FirConstructor by lazy {
      val factoryConstructor = createConstructor(
        owner = generatedClassSymbol,
        key = Key,
        isPrimary = true,
        generateDelegatedNoArgConstructorCall = true,
      ) {
        for (param in matchedConstructorSymbol.valueParameterSymbols) {
          valueParameter(
            name = param.name,
            type = param.resolvedReturnType.wrapInProvider(session.symbolProvider),
          )
        }
      }
      factoryConstructor
    }
    val generatedCallableIdToParameters: Map<CallableId, FirValueParameterSymbol> by lazy {
      generatedConstructor.symbol.valueParameterSymbols.associateBy { it.callableId() }
    }

    // Reference https://github.com/JetBrains/kotlin/blob/436fb8fdfabc3439fde17eca5aac941ecc0170dc/plugins/plugin-sandbox/src/org/jetbrains/kotlin/plugin/sandbox/fir/generators/CompanionGenerator.kt#L33
    val generatedCompanionClass: FirClassSymbol<*> by lazy {
      createCompanionObject(generatedClassSymbol, Key) {
        this@createCompanionObject.visibility = Visibilities.Public
      }.symbol
    }

    val generatedCompanionConstructor: FirConstructor by lazy {
      createDefaultPrivateConstructor(generatedCompanionClass, Key)
    }

    private fun FirValueParameterSymbol.callableId(): CallableId {
      return CallableId(classId = generatedClassId, callableName = name)
    }
  }
}
