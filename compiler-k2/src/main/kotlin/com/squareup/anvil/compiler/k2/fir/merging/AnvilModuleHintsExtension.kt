package com.squareup.anvil.compiler.k2.fir.merging

import com.google.auto.service.AutoService
import com.squareup.anvil.compiler.k2.fir.AnvilFirContext
import com.squareup.anvil.compiler.k2.fir.AnvilFirDeclarationGenerationExtension
import com.squareup.anvil.compiler.k2.fir.AnvilFirExtensionFactory
import com.squareup.anvil.compiler.k2.fir.abstraction.AnvilContributedModulesGenerator
import com.squareup.anvil.compiler.k2.fir.abstraction.providers.anvilFirScopedContributionProvider
import com.squareup.anvil.compiler.k2.fir.contributions.ContributedModule
import com.squareup.anvil.compiler.k2.utils.fir.AnvilPredicates
import com.squareup.anvil.compiler.k2.utils.names.FqNames
import com.squareup.anvil.compiler.k2.utils.stdlib.mapToSet
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.caches.firCachesFactory
import org.jetbrains.kotlin.fir.caches.getValue
import org.jetbrains.kotlin.fir.extensions.ExperimentalTopLevelDeclarationsGenerationApi
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

public class AnvilModuleHintsExtension(
  anvilFirContext: AnvilFirContext,
  session: FirSession,
) : AnvilFirDeclarationGenerationExtension(anvilFirContext, session) {

  private val contributedModules by session.firCachesFactory.createLazyValue {
    session.anvilFirScopedContributionProvider.getContributions()
      .filterIsInstance<ContributedModule>()
  }

  private val contributedModulesGenerator by session.firCachesFactory.createLazyValue {
    AnvilContributedModulesGenerator(anvilFirContext, session)
  }

  private val moduleHintClasses by session.firCachesFactory.createLazyValue {
    contributedModulesGenerator.doThings(contributedModules, firExtension = this)
  }

  private val moduleHintClassesMap by session.firCachesFactory.createLazyValue {
    moduleHintClasses.associateBy { it.classId }
  }

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    register(AnvilPredicates.hasAnyAnvilContributes)
  }

  override fun hasPackage(packageFqName: FqName): Boolean {
    return packageFqName == FqNames.anvilHintPackage && contributedModules.isNotEmpty()
  }

  @ExperimentalTopLevelDeclarationsGenerationApi
  override fun generateTopLevelClassLikeDeclaration(classId: ClassId): FirClassLikeSymbol<*> {
    return moduleHintClassesMap.getValue(classId).generatedClass.getValue().symbol
  }

  @ExperimentalTopLevelDeclarationsGenerationApi
  override fun getTopLevelClassIds(): Set<ClassId> {

    return moduleHintClasses.mapToSet { it.classId }
    // return if (session.anvilFirScopedContributionProvider.isInitialized()) {
    //   moduleHintClasses.mapToSet { it.classId }
    // } else {
    //   emptySet()
    // }
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
