package com.squareup.anvil.compiler.k2.fir.abstraction.extensions

import com.squareup.anvil.compiler.k2.fir.TopLevelClassProcessor
import com.squareup.anvil.compiler.k2.fir.abstraction.providers.anvilFirProcessorProvider
import com.squareup.anvil.compiler.k2.utils.fir.AnvilPredicates
import com.squareup.anvil.compiler.k2.utils.fir.wrapInSyntheticFile
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.caches.firCachesFactory
import org.jetbrains.kotlin.fir.caches.getValue
import org.jetbrains.kotlin.fir.extensions.ExperimentalTopLevelDeclarationsGenerationApi
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

public class TopLevelClassProcessorExtension(session: FirSession) :
  FirDeclarationGenerationExtension(session) {

  private val topLevelByClassId = mutableMapOf<ClassId, TopLevelClassProcessor>()

  private val generators by session.firCachesFactory.createLazyValue {
    session.anvilFirProcessorProvider.topLevelClassProcessors
  }

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    register(AnvilPredicates.hasAnyAnvilContributes)
  }

  override fun hasPackage(packageFqName: FqName): Boolean {
    return generators.any { it.hasPackage(packageFqName) }
  }

  @ExperimentalTopLevelDeclarationsGenerationApi
  override fun getTopLevelClassIds(): Set<ClassId> {

    for (generator in generators) {
      for (id in generator.getTopLevelClassIds()) {
        topLevelByClassId[id] = generator
      }
    }

    return topLevelByClassId.keys
  }

  @ExperimentalTopLevelDeclarationsGenerationApi
  override fun generateTopLevelClassLikeDeclaration(
    classId: ClassId,
  ): FirClassLikeSymbol<*>? {
    return topLevelByClassId[classId]
      ?.generateTopLevelClassLikeDeclaration(classId, firExtension = this)
      ?.generatedClass
      ?.getValue()
      ?.wrapInSyntheticFile(session)
      ?.symbol
  }

  override fun getCallableNamesForClass(
    classSymbol: FirClassSymbol<*>,
    context: MemberGenerationContext,
  ): Set<Name> = topLevelByClassId[classSymbol.classId]
    ?.getCallableNamesForClass(classSymbol, context)
    .orEmpty()

  override fun generateFunctions(
    callableId: CallableId,
    context: MemberGenerationContext?,
  ): List<FirNamedFunctionSymbol> {
    return topLevelByClassId[callableId.classId]
      ?.generateFunctions(callableId, context, firExtension = this)
      .orEmpty()
  }
}
