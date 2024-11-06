package com.squareup.anvil.compiler.k2

import com.squareup.anvil.compiler.k2.internal.Names
import com.squareup.anvil.compiler.k2.internal.anvilPrefix
import com.squareup.anvil.compiler.k2.internal.hasAnvilPrefix
import com.squareup.anvil.compiler.k2.internal.mapToSet
import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.ExperimentalTopLevelDeclarationsGenerationApi
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.predicate.LookupPredicate
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.plugin.createTopLevelClass
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

@OptIn(ExperimentalTopLevelDeclarationsGenerationApi::class)
public class AnvilComponentSubtypeDeclarationGenerationExtension(session: FirSession) :
  FirDeclarationGenerationExtension(session) {

  private companion object {
    private val PREDICATE = LookupPredicate.create {
      annotated(Names.mergeComponentFir)
    }
  }

  public object Key : GeneratedDeclarationKey() {
    override fun toString(): String {
      return "${AnvilComponentSubtypeDeclarationGenerationExtension::class.simpleName}-Key"
    }
  }

  private val predicateBasedProvider = session.predicateBasedProvider
  private val matchedClasses by lazy {
    predicateBasedProvider.getSymbolsByPredicate(PREDICATE)
      .filterIsInstance<FirRegularClassSymbol>()
  }

  private val classIdsForMatchedClasses: Set<ClassId> by lazy {
    matchedClasses.mapToSet(transform = FirRegularClassSymbol::classId)
  }

  override fun hasPackage(packageFqName: FqName): Boolean {
    // error("Why is this never called? $packageFqName")
    return packageFqName == Names.foo
  }

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    register(PREDICATE)
  }

  override fun getTopLevelClassIds(): Set<ClassId> {
    return classIdsForMatchedClasses.mapToSet { it.anvilPrefix() }
  }

  override fun generateTopLevelClassLikeDeclaration(classId: ClassId): FirClassLikeSymbol<*>? {
    if (matchedClasses.isEmpty()) return null
    if (!classId.hasAnvilPrefix()) return null

    return generateComponentSubtype(classId)
  }

  @OptIn(SymbolInternals::class)
  private fun generateComponentSubtype(ownerClassId: ClassId): FirClassLikeSymbol<*> {

    error("This is not working as expected.")

    val newSubtype = createTopLevelClass(
      classId = ownerClassId,
      key = Key,
      classKind = ClassKind.INTERFACE,
    ) {
      // superType(
      //   Names.testComponent.classId()
      //     .constructClassLikeType(
      //       typeArguments = emptyArray(),
      //       isNullable = false,
      //       attributes = ConeAttributes.Empty,
      //     ),
      // )
    }
      .apply {
        addMergedComponentAnnotation(session, TODO())
      }
    return newSubtype.symbol
  }
}
