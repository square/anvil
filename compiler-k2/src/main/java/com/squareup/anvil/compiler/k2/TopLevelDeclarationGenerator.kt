package com.squareup.anvil.compiler.k2

import com.squareup.anvil.compiler.k2.internal.fqn
import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.ExperimentalTopLevelDeclarationsGenerationApi
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.extensions.predicate.LookupPredicate
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId

@OptIn(ExperimentalTopLevelDeclarationsGenerationApi::class)
public class TopLevelDeclarationsGenerator(session: FirSession) :
  FirDeclarationGenerationExtension(session) {

  public companion object {
    private val PREDICATE = LookupPredicate.create { annotated("DummyFunction".fqn()) }
  }

  private val predicateBasedProvider by lazy { session.predicateBasedProvider }
  private val matchedClasses by lazy {
    predicateBasedProvider.getSymbolsByPredicate(PREDICATE)
      .filterIsInstance<FirRegularClassSymbol>()
  }

  override fun getTopLevelCallableIds(): Set<CallableId> {
    return emptySet()
    // return matchedClasses.mapTo(mutableSetOf()) {
    //   val classId = it.classId
    //   CallableId(classId.packageFqName, Name.identifier(classId.toDummyCallableName()))
    // }
  }

  override fun generateFunctions(
    callableId: CallableId,
    context: MemberGenerationContext?,
  ): List<FirNamedFunctionSymbol> {
    return emptyList()
    // if (context != null) return emptyList()
    // val matchedClassSymbol = findMatchedClassForFunction(callableId) ?: return emptyList()
    // val function =
    //   createTopLevelFunction(Key, callableId, session.builtinTypes.stringType.coneType) {
    //     valueParameter(Name.identifier("value"), matchedClassSymbol.constructStarProjectedType())
    //   }
    // return listOf(function.symbol)
  }

  private fun findMatchedClassForFunction(callableId: CallableId): FirRegularClassSymbol? {
    // We generate only top-level functions
    if (callableId.classId != null) return null
    return matchedClasses
      .filter { it.classId.packageFqName == callableId.packageName }
      .firstOrNull { callableId.callableName.identifier == it.classId.toDummyCallableName() }
  }

  private fun ClassId.toDummyCallableName(): String = "dummy${shortClassName.identifier}"

  public object Key : GeneratedDeclarationKey()

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    register(PREDICATE)
  }
}
