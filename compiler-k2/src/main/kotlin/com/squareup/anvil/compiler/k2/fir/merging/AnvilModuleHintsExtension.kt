package com.squareup.anvil.compiler.k2.fir.merging

import com.google.auto.service.AutoService
import com.squareup.anvil.compiler.k2.fir.AnvilFirContext
import com.squareup.anvil.compiler.k2.fir.AnvilFirDeclarationGenerationExtension
import com.squareup.anvil.compiler.k2.fir.AnvilFirExtensionFactory
import com.squareup.anvil.compiler.k2.fir.abstraction.AnvilContributedModulesGenerator
import com.squareup.anvil.compiler.k2.fir.abstraction.providers.RequiresTypesResolutionPhase
import com.squareup.anvil.compiler.k2.fir.abstraction.providers.scopedContributionProvider
import com.squareup.anvil.compiler.k2.utils.fir.AnvilPredicates
import com.squareup.anvil.compiler.k2.utils.stdlib.mapToSet
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.caches.FirLazyValue
import org.jetbrains.kotlin.fir.caches.firCachesFactory
import org.jetbrains.kotlin.fir.caches.getValue
import org.jetbrains.kotlin.fir.extensions.ExperimentalTopLevelDeclarationsGenerationApi
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.name.ClassId

public class AnvilModuleHintsExtension(
  anvilFirContext: AnvilFirContext,
  session: FirSession,
) : AnvilFirDeclarationGenerationExtension(anvilFirContext, session) {

  private val cachesFactory = session.firCachesFactory

  private fun <V> createLazyValue(createValue: () -> V): FirLazyValue<V> =
    cachesFactory.createLazyValue(createValue)

  @OptIn(RequiresTypesResolutionPhase::class)
  private val contributedModules by createLazyValue {
    session.scopedContributionProvider.contributedModules
      .plus(session.scopedContributionProvider.contributedBindingModules)
  }

  private val moduleHintClasses by createLazyValue {
    AnvilContributedModulesGenerator(anvilFirContext, session)
      .doThings(contributedModules, firExtension = this)
  }

  private val moduleHintClassesMap by createLazyValue {
    moduleHintClasses.associateBy { it.classId }
  }

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    register(AnvilPredicates.hasAnyAnvilContributes)
  }

  @ExperimentalTopLevelDeclarationsGenerationApi
  override fun generateTopLevelClassLikeDeclaration(
    classId: ClassId,
  ): FirClassLikeSymbol<*>? {
    return moduleHintClassesMap[classId]?.generatedClass?.getValue()?.symbol
  }

  @ExperimentalTopLevelDeclarationsGenerationApi
  override fun getTopLevelClassIds(): Set<ClassId> {
    return moduleHintClasses.mapToSet { it.classId }
  }
}

@AutoService(AnvilFirExtensionFactory::class)
public class AnvilModuleHintsExtensionFactory : AnvilFirDeclarationGenerationExtension.Factory {
  override fun create(
    anvilFirContext: AnvilFirContext,
  ): FirDeclarationGenerationExtension.Factory {
    return FirDeclarationGenerationExtension.Factory { session ->
      AnvilModuleHintsExtension(anvilFirContext, session)
    }
  }
}
