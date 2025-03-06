package com.squareup.anvil.compiler.k2.fir.contributions

import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.compiler.k2.fir.AnvilFirContext
import com.squareup.anvil.compiler.k2.fir.AnvilFirDeclarationGenerationExtension
import com.squareup.anvil.compiler.k2.utils.fir.AnvilPredicates
import com.squareup.anvil.compiler.k2.utils.fir.createFirAnnotation
import com.squareup.anvil.compiler.k2.utils.names.ClassIds
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.caches.contains
import org.jetbrains.kotlin.fir.extensions.ExperimentalTopLevelDeclarationsGenerationApi
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.plugin.createMemberFunction
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.types.constructType
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name

/**
 * Generates binding modules for every [ContributesBinding]-annotated class.
 *
 * Given a Kotlin source file like:
 * ```
 * @ContributesBinding(Any::class)
 * class Foo @Inject constructor() : Bar
 * ```
 *
 * We will generate the FIR equivalent of:
 * ```
 * @ContributesTo(Any::class)
 * @Module
 * abstract class Foo_BindingModule {
 *  @Binds
 *  abstract fun bindBar(impl: Foo): Bar
 * }
 * ```
 */
@OptIn(ExperimentalTopLevelDeclarationsGenerationApi::class)
public class ContributesBindingFirExtension(
  anvilFirContext: AnvilFirContext,
  session: FirSession,
) : AnvilFirDeclarationGenerationExtension(anvilFirContext, session) {

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    register(AnvilPredicates.hasAnvilContributesBinding)
  }

  override fun getTopLevelClassIds(): Set<ClassId> {
    return session.contributesBindingSessionComponent.generatedIdsToMatchedSymbols.keys
  }

  override fun generateTopLevelClassLikeDeclaration(classId: ClassId): FirClassLikeSymbol<*> {
    return session.contributesBindingSessionComponent
      .bindingModuleCache
      .getValue(classId, session)
      .generatedClassSymbol
  }

  override fun getCallableNamesForClass(
    classSymbol: FirClassSymbol<*>,
    context: MemberGenerationContext,
  ): Set<Name> {
    val cache = session.contributesBindingSessionComponent.bindingModuleCache
    if (!cache.contains(classSymbol.classId)) {
      return emptySet()
    }
    val bindingData = cache.getValue(classSymbol.classId, session)

    return setOf(bindingData.callableName)
  }

  override fun generateFunctions(
    callableId: CallableId,
    context: MemberGenerationContext?,
  ): List<FirNamedFunctionSymbol> {
    val bindingData = session.contributesBindingSessionComponent.bindingModuleCache.getValue(
      context!!.owner.classId,
      session,
    )

    return listOf(
      createMemberFunction(
        owner = context.owner,
        key = GeneratedBindingDeclarationKey,
        name = callableId.callableName,
        returnType = bindingData.boundType,
      ) {
        modality = Modality.ABSTRACT
        valueParameter(
          name = Name.identifier("concreteType"),
          type = bindingData.matchedClassSymbol.constructType(),
        )
      }.apply {
        replaceAnnotations(listOf(createFirAnnotation(ClassIds.daggerBinds)))
      }.symbol,
    )
  }
}
