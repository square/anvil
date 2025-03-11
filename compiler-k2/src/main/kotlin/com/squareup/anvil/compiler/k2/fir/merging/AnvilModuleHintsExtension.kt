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
import org.jetbrains.kotlin.fir.caches.FirLazyValue
import org.jetbrains.kotlin.fir.caches.firCachesFactory
import org.jetbrains.kotlin.fir.caches.getValue
import org.jetbrains.kotlin.fir.extensions.ExperimentalTopLevelDeclarationsGenerationApi
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.resolve.providers.dependenciesSymbolProvider
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName

public class AnvilModuleHintsExtension(
  anvilFirContext: AnvilFirContext,
  session: FirSession,
) : AnvilFirDeclarationGenerationExtension(anvilFirContext, session) {

  private val cachesFactory = session.firCachesFactory

  private fun <V> createLazyValue(createValue: () -> V): FirLazyValue<V> =
    cachesFactory.createLazyValue(createValue)

  private val contributedModules by createLazyValue {
    session.anvilFirScopedContributionProvider.getContributions()
      .filterIsInstance<ContributedModule>()
  }

  private val contributedModulesGenerator by createLazyValue {
    AnvilContributedModulesGenerator(anvilFirContext, session)
  }

  private val moduleHintClasses by createLazyValue {
    contributedModulesGenerator.doThings(contributedModules, firExtension = this)
  }

  private val moduleHintClassesMap by createLazyValue {
    moduleHintClasses.associateBy { it.callableId }
  }

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    register(AnvilPredicates.hasAnyAnvilContributes)
  }

  override fun hasPackage(packageFqName: FqName): Boolean {
    return packageFqName == FqNames.anvilHintPackage // && contributedModules.isNotEmpty()
  }

  override fun generateProperties(
    callableId: CallableId,
    context: MemberGenerationContext?,
  ): List<FirPropertySymbol> {

    val names = session.symbolProvider.symbolNamesProvider
      .getTopLevelCallableNamesInPackage(FqNames.anvilHintPackage)

    val deps = session.dependenciesSymbolProvider.symbolNamesProvider
      .getPackageNamesWithTopLevelClassifiers()
    // .getTopLevelCallableNamesInPackage(FqNames.anvilHintPackage)

    println(
      """
      |======================================= ${session.moduleData.name}  generateProperties names
      | -- names
      |${names?.joinToString("\n")}
      |
      | -- deps
      |${deps?.joinToString("\n")}
      |=======================================
      """.trimMargin(),
    )

    return listOf(moduleHintClassesMap.getValue(callableId).generatedProperty.getValue().symbol)
  }

  @ExperimentalTopLevelDeclarationsGenerationApi
  override fun getTopLevelCallableIds(): Set<CallableId> =
    moduleHintClasses.mapToSet { it.callableId }
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
