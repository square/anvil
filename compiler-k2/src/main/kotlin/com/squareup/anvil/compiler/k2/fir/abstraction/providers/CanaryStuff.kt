package com.squareup.anvil.compiler.k2.fir.abstraction.providers

import com.squareup.anvil.compiler.k2.fir.AnvilFirContext
import com.squareup.anvil.compiler.k2.fir.AnvilFirDeclarationGenerationExtension
import com.squareup.anvil.compiler.k2.fir.AnvilFirSupertypeGenerationExtension
import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirClassLikeDeclaration
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.extensions.ExperimentalTopLevelDeclarationsGenerationApi
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.extensions.predicate.LookupPredicate
import org.jetbrains.kotlin.fir.plugin.createTopLevelProperty
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

public class CanaryGen(anvilFirContext: AnvilFirContext, session: FirSession) :
  AnvilFirDeclarationGenerationExtension(anvilFirContext, session) {

  internal companion object KEY : GeneratedDeclarationKey()

  override fun hasPackage(packageFqName: FqName): Boolean {
    return packageFqName == PLUTO.packageFqName
  }

  @ExperimentalTopLevelDeclarationsGenerationApi
  override fun getTopLevelCallableIds(): Set<CallableId> {
    require(didPluto) { "Pluto was not done yet" }
    return setOf(CallableId(PLUTO.packageFqName, Name.identifier("pluto_hint")))
  }

  @OptIn(ExperimentalTopLevelDeclarationsGenerationApi::class)
  override fun generateProperties(
    callableId: CallableId,
    context: MemberGenerationContext?,
  ): List<FirPropertySymbol> = listOf(
    createTopLevelProperty(
      key = KEY,
      callableId = callableId,
      returnType = session.builtinTypes.unitType.coneType,
    ).symbol,
  )
}
private val DINOSAUR = ClassId(FqName("com.squareup.test"), Name.identifier("Dinosaur"))
private val PLUTO = ClassId(FqName("planets"), Name.identifier("Pluto"))
private var didPluto = false

public class CanarySuper(
  anvilFirContext: AnvilFirContext,
  session: FirSession,
) : AnvilFirSupertypeGenerationExtension(anvilFirContext, session) {

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    register(LookupPredicate.create { annotated(DINOSAUR.asSingleFqName()) })
  }

  override fun needTransformSupertypes(declaration: FirClassLikeDeclaration): Boolean {
    return declaration.hasAnnotation(DINOSAUR, session)
  }

  override fun computeAdditionalSupertypes(
    classLikeDeclaration: FirClassLikeDeclaration,
    resolvedSupertypes: List<FirResolvedTypeRef>,
    typeResolver: TypeResolveService,
  ): List<ConeKotlinType> {
    didPluto = true
    return emptyList()
  }
}

// @AutoService(AnvilFirExtensionFactory::class)
// public class CanarySuperFactory : AnvilFirSupertypeGenerationExtension.Factory {
//   override fun create(anvilFirContext: AnvilFirContext): FirSupertypeGenerationExtension.Factory {
//     return FirSupertypeGenerationExtension.Factory { session ->
//       CanarySuper(anvilFirContext, session)
//     }
//   }
// }
//
//
// @AutoService(AnvilFirExtensionFactory::class)
// public class CanaryGenFactory : AnvilFirDeclarationGenerationExtension.Factory {
//   override fun create(anvilFirContext: AnvilFirContext): FirDeclarationGenerationExtension.Factory {
//     return FirDeclarationGenerationExtension.Factory { session ->
//       CanaryGen(anvilFirContext, session)
//     }
//   }
// }
