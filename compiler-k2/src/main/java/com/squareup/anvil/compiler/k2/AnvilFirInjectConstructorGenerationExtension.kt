package com.squareup.anvil.compiler.k2

import com.squareup.anvil.compiler.k2.internal.DefaultGeneratedDeclarationKey
import com.squareup.anvil.compiler.k2.internal.Names
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.predicate.LookupPredicate
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

public class AnvilFirInjectConstructorGenerationExtension(session: FirSession) :
  FirDeclarationGenerationExtension(session) {
  public companion object {
    private val INJECT = ClassId.topLevel(Names.inject)

    private val PREDICATE = LookupPredicate.create {
      annotated(Names.inject) or annotatedOrUnder(Names.inject)
    }
  }

  private object Key : DefaultGeneratedDeclarationKey()

  private val predicateBasedProvider = session.predicateBasedProvider
  private val matchedClasses by lazy {
    predicateBasedProvider.getSymbolsByPredicate(PREDICATE)
      .filterIsInstance<FirRegularClassSymbol>()
  }

  // @ExperimentalTopLevelDeclarationsGenerationApi
  // override fun getTopLevelClassIds(): Set<ClassId> {
  //   val ktModule = (session.moduleData as LLFirModuleData).ktModule
  //
  //   listOf(
  //     "project" to ktModule.project.toStringPretty(),
  //     "platform" to ktModule.platform.toStringPretty(),
  //   )
  //     .joinToString(
  //       separator = "-----------\n\n",
  //       prefix = "-------------------",
  //       postfix = "-------------------",
  //     ) { "${it.first}  --  ${it.second}" }
  //     .let { throw AnvilCompilationException(it) }
  //
  //   return super.getTopLevelClassIds()
  // }

  override fun hasPackage(packageFqName: FqName): Boolean = true

  // @OptIn(ExperimentalTopLevelDeclarationsGenerationApi::class)
  // override fun generateTopLevelClassLikeDeclaration(classId: ClassId): FirClassLikeSymbol<*>? {
  //
  //   throw AnvilCompilationException(
  //     """
  //       ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
  //       input class id: $classId
  //       ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
  //     """.trimIndent(),
  //   )
  //
  //   return createTopLevelClass(classId.factory(), Key).symbol
  // }

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    // register(PREDICATE)
  }

  // @OptIn(ExperimentalTopLevelDeclarationsGenerationApi::class)
  // override fun generateTopLevelClassLikeDeclaration(classId: ClassId): FirClassLikeSymbol<*>? {
  //   if (classId != GENERATED_CLASS_ID) return null
  //   return createTopLevelClass(classId, Key).symbol
  // }
}
