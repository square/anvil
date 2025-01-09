package com.squareup.anvil.compiler.k2

import com.squareup.anvil.compiler.k2.internal.AnvilPredicates
import com.squareup.anvil.compiler.k2.internal.Names
import com.squareup.anvil.compiler.k2.internal.classId
import com.squareup.anvil.compiler.k2.internal.isFactoryDelegate
import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirDeclarationDataKey
import org.jetbrains.kotlin.fir.declarations.FirDeclarationDataRegistry
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.impl.FirResolvedDeclarationStatusImpl
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotation
import org.jetbrains.kotlin.fir.expressions.impl.FirEmptyAnnotationArgumentMapping
import org.jetbrains.kotlin.fir.extensions.ExperimentalTopLevelDeclarationsGenerationApi
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.plugin.ClassBuildingContext
import org.jetbrains.kotlin.fir.plugin.createConstructor
import org.jetbrains.kotlin.fir.plugin.createMemberFunction
import org.jetbrains.kotlin.fir.plugin.createTopLevelClass
import org.jetbrains.kotlin.fir.resolve.defaultType
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.toFirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.constructStarProjectedType
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames

@OptIn(ExperimentalTopLevelDeclarationsGenerationApi::class)
public class AnvilFactoryDelegateDeclarationGenerationExtension(session: FirSession) :
  FirDeclarationGenerationExtension(session) {

  public object Key : GeneratedDeclarationKey() {
    override fun toString(): String {
      return "${AnvilFactoryDelegateDeclarationGenerationExtension::class.simpleName}-Key"
    }
  }

  // private val predicateBasedProvider = session.predicateBasedProvider
  // private val matchedClasses by lazy {
  //   predicateBasedProvider.getSymbolsByPredicate(AnvilPredicates.hasInjectAnnotation)
  //     .filterIsInstance<FirConstructorSymbol>()
  // }
  //
  // private val classIdsForMatchedClasses: Set<ClassId> by lazy {
  //   matchedClasses.mapToSet { it.callableId.classId!! }
  // }

  override fun getTopLevelClassIds(): Set<ClassId> {
    return setOf("foo.OtherClass".classId())
    // return classIdsForMatchedClasses.mapToSet { it.factoryDelegate() }
  }

  override fun generateTopLevelClassLikeDeclaration(classId: ClassId): FirClassLikeSymbol<*>? {
    // if (matchedClasses.isEmpty()) return null

    if (!classId.isFactoryDelegate()) return null

    return createTopLevelClass(classId, Key) {
      val clazz: ClassBuildingContext = this
      clazz.visibility = Visibilities.Private
      clazz.modality = Modality.ABSTRACT
      clazz.status {
        val s: FirResolvedDeclarationStatusImpl = this
      }
    }
      .symbol
  }

  override fun getCallableNamesForClass(
    classSymbol: FirClassSymbol<*>,
    context: MemberGenerationContext,
  ): Set<Name> {

    if (!classSymbol.classId.isFactoryDelegate()) return emptySet()

    return setOf(SpecialNames.INIT)
  }

  override fun generateConstructors(context: MemberGenerationContext): List<FirConstructorSymbol> {

    val classId = context.owner.classId

    if (!classId.isFactoryDelegate()) return emptyList()

    return listOf(
      createConstructor(
        owner = context.owner,
        key = Key,
        isPrimary = true,
        generateDelegatedNoArgConstructorCall = true,
      ) {
        valueParameter(Name.identifier("thing"), session.builtinTypes.stringType.coneType)
      }
        .apply {
          replaceAnnotations(
            listOf(
              buildAnnotation {
                annotationTypeRef = session.symbolProvider
                  .getClassLikeSymbolByClassId(Names.inject.classId())
                  .let { it as FirRegularClassSymbol }
                  .defaultType()
                  .toFirResolvedTypeRef()
                argumentMapping = FirEmptyAnnotationArgumentMapping
              },
            ),
          )
        }
        .symbol,
    )
  }

  override fun generateFunctions(
    callableId: CallableId,
    context: MemberGenerationContext?,
  ): List<FirNamedFunctionSymbol> {
    if (callableId.classId != "foo.OtherClass".classId()) return emptyList()
    // if (callableId.classId !in classIdsForMatchedClasses) return emptyList()
    val owner = context?.owner
    require(owner is FirRegularClassSymbol)
    val matchedClassId = owner.matchedClass ?: return emptyList()
    val matchedClassSymbol =
      session.getRegularClassSymbolByClassId(matchedClassId) ?: return emptyList()
    val function = createMemberFunction(
      owner = owner,
      key = Key,
      name = callableId.callableName,
      returnType = matchedClassSymbol.constructStarProjectedType(),
    )
    return listOf(function.symbol)
  }

  override fun hasPackage(packageFqName: FqName): Boolean = packageFqName == Names.foo.packageFqName

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    register(AnvilPredicates.hasInjectAnnotation)
  }
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
