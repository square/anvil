package com.squareup.anvil.compiler.k2.fir.merging

import com.google.auto.service.AutoService
import com.squareup.anvil.compiler.k2.fir.AnvilFirContext2
import com.squareup.anvil.compiler.k2.fir.AnvilFirProcessor
import com.squareup.anvil.compiler.k2.fir.RequiresTypesResolutionPhase
import com.squareup.anvil.compiler.k2.fir.SupertypeProcessor
import com.squareup.anvil.compiler.k2.fir.abstraction.providers.anvilFirDependencyHintProvider
import com.squareup.anvil.compiler.k2.fir.abstraction.providers.anvilFirSymbolProvider
import com.squareup.anvil.compiler.k2.fir.abstraction.providers.scopedContributionProvider
import com.squareup.anvil.compiler.k2.utils.stdlib.mapToSet
import org.jetbrains.kotlin.fir.caches.getValue
import org.jetbrains.kotlin.fir.declarations.FirClassLikeDeclaration
import org.jetbrains.kotlin.fir.declarations.utils.classId
import org.jetbrains.kotlin.fir.extensions.FirSupertypeGenerationExtension
import org.jetbrains.kotlin.fir.plugin.createConeType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.classId

@AutoService(AnvilFirProcessor.Factory::class)
public class InterfaceMergingGeneratorFactory : AnvilFirProcessor.Factory {
  override fun create(anvilFirContext: AnvilFirContext2): AnvilFirProcessor {
    return InterfaceMergingGenerator(anvilFirContext)
  }
}

/**
 * This extension finds all contributed component interfaces and adds them as super types to Dagger
 * components annotated with `@MergeComponent`
 */
public class InterfaceMergingGenerator(
  override val anvilFirContext: AnvilFirContext2,
) : SupertypeProcessor() {

  private val mergedComponentIds by lazyValue {
    session.anvilFirSymbolProvider.mergeComponentSymbols.mapToSet { it.classId }
  }

  @RequiresTypesResolutionPhase
  private val mergedSupertypesByScope by lazyValue {
    val sourceSupers = session.scopedContributionProvider.contributedSupertypes
    val dependencySupers = session.anvilFirDependencyHintProvider.allDependencyContributedComponents

    (sourceSupers + dependencySupers).groupBy { it.scopeType.getValue() }
  }

  override fun shouldProcess(declaration: FirClassLikeDeclaration): Boolean {
    return declaration.classId in mergedComponentIds
  }

  @OptIn(RequiresTypesResolutionPhase::class)
  override fun addSupertypes(
    classLikeDeclaration: FirClassLikeDeclaration,
    resolvedSupertypes: List<FirResolvedTypeRef>,
    typeResolver: FirSupertypeGenerationExtension.TypeResolveService,
  ): List<ConeKotlinType> {

    val existingSupertypes = resolvedSupertypes.mapToSet { it.coneType.classId }

    val mergedComponent =
      session.scopedContributionProvider.mergedComponents
        .single { it.containingDeclaration.getValue().classId == classLikeDeclaration.classId }

    val mergeScopeId = mergedComponent.scopeType.getValue()

    return mergedSupertypesByScope[mergeScopeId]
      ?.filter { it.contributedType !in existingSupertypes }
      ?.map { it.contributedType.createConeType(session) }
      .orEmpty()
  }
}
