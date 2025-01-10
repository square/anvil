package com.squareup.anvil.compiler.k2

import com.squareup.anvil.compiler.k2.internal.AnvilPredicates
import com.squareup.anvil.compiler.k2.internal.Names
import com.squareup.anvil.compiler.k2.internal.factoryDelegate
import com.squareup.anvil.compiler.k2.internal.isFactoryDelegate
import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirDeclarationDataKey
import org.jetbrains.kotlin.fir.declarations.FirDeclarationDataRegistry
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.extensions.ExperimentalTopLevelDeclarationsGenerationApi
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.plugin.ClassBuildingContext
import org.jetbrains.kotlin.fir.plugin.createConstructor
import org.jetbrains.kotlin.fir.plugin.createTopLevelClass
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames

public class AnvilFactoryDelegateDeclarationGenerationExtension(session: FirSession) :
  FirDeclarationGenerationExtension(session) {

  public object Key : GeneratedDeclarationKey() {
    override fun toString(): String {
      return "${AnvilFactoryDelegateDeclarationGenerationExtension::class.simpleName}-Key"
    }
  }

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    register(AnvilPredicates.hasInjectAnnotation)
  }

  @ExperimentalTopLevelDeclarationsGenerationApi
  override fun getTopLevelCallableIds(): Set<CallableId> = super.getTopLevelCallableIds()

  override fun generateProperties(
    callableId: CallableId,
    context: MemberGenerationContext?,
  ): List<FirPropertySymbol> = super.generateProperties(callableId, context)

  private val predicateBasedProvider by lazy { session.predicateBasedProvider }
  private val matchedClasses by lazy {
    predicateBasedProvider.getSymbolsByPredicate(AnvilPredicates.hasInjectAnnotation)
      .filterIsInstance<FirConstructorSymbol>()
  }

  private val classIdsForMatchedClasses: Set<ClassId> by lazy {
    matchedClasses.mapToSet { it.callableId.classId!! }
  }

  @ExperimentalTopLevelDeclarationsGenerationApi
  override fun getTopLevelClassIds(): Set<ClassId> {
    return classIdsForMatchedClasses.mapToSet { it.factoryDelegate() }
  }

  private inline fun <C : Collection<T>, T, R> C.mapToSet(
    destination: MutableSet<R> = mutableSetOf(),
    transform: (T) -> R,
  ): Set<R> = mapTo(destination, transform)

  @ExperimentalTopLevelDeclarationsGenerationApi
  override fun generateTopLevelClassLikeDeclaration(classId: ClassId): FirClassLikeSymbol<*>? {
    if (matchedClasses.isEmpty()) return null
    if (!classId.isFactoryDelegate()) return null

    return createTopLevelClass(classId, Key) {
      val clazz: ClassBuildingContext = this
      clazz.visibility = Visibilities.Private
      clazz.modality = Modality.ABSTRACT
    }
      .symbol
  }

  override fun getCallableNamesForClass(
    classSymbol: FirClassSymbol<*>,
    context: MemberGenerationContext,
  ): Set<Name> = when {
    classSymbol.classId.isFactoryDelegate() -> setOf(SpecialNames.INIT)
    else -> emptySet()
  }

  override fun generateConstructors(context: MemberGenerationContext): List<FirConstructorSymbol> {

    val classId = context.owner.classId

    if (!classId.isFactoryDelegate()) return emptyList()

    return listOf(
      createConstructor(
        owner = context.owner,
        key = Key,
        // isPrimary = true,
        // generateDelegatedNoArgConstructorCall = true,
      ) {
        valueParameter(Name.identifier("thing"), session.builtinTypes.stringType.coneType)
      }
        // .apply {
        //   replaceAnnotations(
        //     listOf(
        //       buildAnnotation {
        //         annotationTypeRef = Names.inject.createUserType(null, false)
        //         argumentMapping = FirEmptyAnnotationArgumentMapping
        //       },
        //     ),
        //   )
        // }
        .symbol,
    )
  }

  override fun hasPackage(packageFqName: FqName): Boolean = packageFqName == Names.foo.packageFqName
}

private fun FirSession.getRegularClassSymbolByClassId(classId: ClassId): FirRegularClassSymbol? {
  return symbolProvider.getClassLikeSymbolByClassId(classId) as? FirRegularClassSymbol
}

private object MatchedClassAttributeKey : FirDeclarationDataKey()

private var FirRegularClass.matchedClass: ClassId? by FirDeclarationDataRegistry.data(
  MatchedClassAttributeKey,
)
private val FirRegularClassSymbol.matchedClass: ClassId? by FirDeclarationDataRegistry.symbolAccessor(
  MatchedClassAttributeKey,
)
