package com.squareup.anvil.compiler.k2.fir.abstraction.extensions

import com.squareup.anvil.compiler.k2.fir.RequiresTypesResolutionPhase
import com.squareup.anvil.compiler.k2.fir.SupertypeProcessor
import com.squareup.anvil.compiler.k2.fir.abstraction.providers.anvilFirProcessorProvider
import com.squareup.anvil.compiler.k2.fir.abstraction.providers.scopedContributionProvider
import com.squareup.anvil.compiler.k2.utils.fir.AnvilPredicates
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirClassLikeDeclaration
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.utils.classId
import org.jetbrains.kotlin.fir.extensions.ExperimentalSupertypesGenerationApi
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.FirSupertypeGenerationExtension
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.name.ClassId

public class SupertypeProcessorExtension(
  session: FirSession,
) : FirSupertypeGenerationExtension(session) {

  private val generatorsByClassId = mutableMapOf<ClassId, List<SupertypeProcessor>>()

  @OptIn(RequiresTypesResolutionPhase::class)
  override fun needTransformSupertypes(declaration: FirClassLikeDeclaration): Boolean {

    val processors = session.anvilFirProcessorProvider.supertypeProcessors +
      session.anvilFirProcessorProvider.flushingSupertypeProcessors

    val todo = processors.filter { it.shouldProcess(declaration) }

    if (todo.isNotEmpty()) {
      generatorsByClassId[declaration.classId] = todo
    }

    return todo.isNotEmpty() || !session.scopedContributionProvider.isInitialized()
  }

  @OptIn(RequiresTypesResolutionPhase::class)
  override fun computeAdditionalSupertypes(
    classLikeDeclaration: FirClassLikeDeclaration,
    resolvedSupertypes: List<FirResolvedTypeRef>,
    typeResolver: TypeResolveService,
  ): List<ConeKotlinType> {

    session.scopedContributionProvider.bindTypeResolveService(typeResolver)

    return generatorsByClassId.remove(classLikeDeclaration.classId)
      ?.flatMap { it.addSupertypes(classLikeDeclaration, resolvedSupertypes, typeResolver) }
      ?: emptyList()
  }

  @ExperimentalSupertypesGenerationApi
  override fun computeAdditionalSupertypesForGeneratedNestedClass(
    klass: FirRegularClass,
    typeResolver: TypeResolveService,
  ): List<FirResolvedTypeRef> {
    return generatorsByClassId.remove(klass.classId)
      ?.flatMap { it.computeAdditionalSupertypesForGeneratedNestedClass(klass, typeResolver) }
      ?: emptyList()
  }

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    register(AnvilPredicates.hasAnyAnvilContributes)
  }
}
