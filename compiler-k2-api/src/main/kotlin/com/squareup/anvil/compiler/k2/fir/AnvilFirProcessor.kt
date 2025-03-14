package com.squareup.anvil.compiler.k2.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.caches.FirCachesFactory
import org.jetbrains.kotlin.fir.caches.FirLazyValue
import org.jetbrains.kotlin.fir.caches.firCachesFactory
import org.jetbrains.kotlin.fir.declarations.FirClassLikeDeclaration
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.extensions.ExperimentalTopLevelDeclarationsGenerationApi
import org.jetbrains.kotlin.fir.extensions.FirExtension
import org.jetbrains.kotlin.fir.extensions.FirSupertypeGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirSupertypeGenerationExtension.TypeResolveService
import org.jetbrains.kotlin.fir.extensions.predicate.LookupPredicate
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

public sealed class AnvilFirProcessor : HasAnvilFirContext {

  protected val session: FirSession get() = anvilFirContext.session
  protected val cachesFactory: FirCachesFactory get() = session.firCachesFactory

  protected inline fun <T, R> FirLazyValue<T>.map(
    crossinline transform: (T) -> R,
  ): FirLazyValue<R> = lazyValue { transform(this.getValue()) }

  protected inline fun <T> lazyValue(crossinline initializer: () -> T): FirLazyValue<T> {
    return cachesFactory.createLazyValue { initializer() }
  }

  protected fun lazySymbols(
    predicate: LookupPredicate,
  ): FirLazyValue<List<FirBasedSymbol<*>>> = lazyValue {
    session.predicateBasedProvider.getSymbolsByPredicate(predicate)
  }

  protected inline fun <reified T> lazySymbolsOf(
    predicate: LookupPredicate,
  ): FirLazyValue<List<T>> = lazySymbols(predicate).map { it.filterIsInstance<T>() }

  public fun interface Factory {
    public fun create(anvilFirContext: AnvilFirContext2): AnvilFirProcessor
  }
}

public abstract class TopLevelClassProcessor : AnvilFirProcessor() {

  @ExperimentalTopLevelDeclarationsGenerationApi
  public abstract fun getTopLevelClassIds(): Set<ClassId>
  public abstract fun hasPackage(packageFqName: FqName): Boolean

  @ExperimentalTopLevelDeclarationsGenerationApi
  public abstract fun generateTopLevelClassLikeDeclaration(
    classId: ClassId,
    firExtension: FirExtension,
  ): PendingTopLevelClass
}

public abstract class SupertypeProcessor : AnvilFirProcessor() {
  public abstract fun shouldProcess(declaration: FirClassLikeDeclaration): Boolean
  public open fun addSupertypes(
    classLikeDeclaration: FirClassLikeDeclaration,
    resolvedSupertypes: List<FirResolvedTypeRef>,
    typeResolver: FirSupertypeGenerationExtension.TypeResolveService,
  ): List<ConeKotlinType> = emptyList()

  public open fun computeAdditionalSupertypesForGeneratedNestedClass(
    klass: FirRegularClass,
    typeResolver: FirSupertypeGenerationExtension.TypeResolveService,
  ): List<FirResolvedTypeRef> = emptyList()
}

public abstract class FlushingSupertypeProcessor : SupertypeProcessor() {

  @OptIn(RequiresTypesResolutionPhase::class)
  public override fun addSupertypes(
    classLikeDeclaration: FirClassLikeDeclaration,
    resolvedSupertypes: List<FirResolvedTypeRef>,
    typeResolver: TypeResolveService,
  ): List<ConeKotlinType> {

    classLikeDeclaration.replaceAnnotations(
      classLikeDeclaration.annotations + generateAnnotation(classLikeDeclaration),
    )

    return emptyList()
  }

  @RequiresTypesResolutionPhase
  public abstract fun generateAnnotation(
    classLikeDeclaration: FirClassLikeDeclaration,
  ): FirAnnotationCall
}
