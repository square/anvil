package com.squareup.anvil.compiler.k2.fir.abstraction.providers

import com.squareup.anvil.compiler.k2.fir.AnvilFirExtensionSessionComponent
import com.squareup.anvil.compiler.k2.fir.ContributedModule
import com.squareup.anvil.compiler.k2.fir.ContributedSupertype
import com.squareup.anvil.compiler.k2.utils.fir.AnvilPredicates
import com.squareup.anvil.compiler.k2.utils.fir.requireArgumentAt
import com.squareup.anvil.compiler.k2.utils.fir.requireRegularClassSymbol
import com.squareup.anvil.compiler.k2.utils.names.ClassIds
import com.squareup.anvil.compiler.k2.utils.names.FqNames
import com.squareup.anvil.compiler.k2.utils.names.Names
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.caches.FirLazyValue
import org.jetbrains.kotlin.fir.caches.firCachesFactory
import org.jetbrains.kotlin.fir.caches.getValue
import org.jetbrains.kotlin.fir.declarations.evaluateAs
import org.jetbrains.kotlin.fir.declarations.getAnnotationByClassId
import org.jetbrains.kotlin.fir.declarations.unwrapVarargValue
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.resolve.providers.dependenciesSymbolProvider
import org.jetbrains.kotlin.fir.utils.exceptions.withFirEntry
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.utils.exceptions.checkWithAttachment
import org.jetbrains.kotlin.utils.sure

public val FirSession.anvilFirDependencyHintProvider: AnvilFirDependencyHintProvider by FirSession.sessionComponentAccessor()

public class AnvilFirDependencyHintProvider(session: FirSession) :
  AnvilFirExtensionSessionComponent(session) {

  private val cachesFactory = session.firCachesFactory

  private fun <V> createLazyValue(createValue: () -> V): FirLazyValue<V> =
    cachesFactory.createLazyValue(createValue)

  private val dependencyHintClassSymbols = createLazyValue {
    // All hint classes are located in the `anvil.hint` package regardless of their module or type,
    // and they are all top-level "classes" (really interfaces). The dependency symbol name provider
    // gives us all their simple names.  We can then reconstruct their ClassIds,
    // and use those ClassIds to load their symbols.
    // This method for finding hints is important in two ways:
    //   1. Those dependency symbols will never be loaded unless they're looked up by name,
    //      which means that this is the only way to get what we need.
    //   2. This is how the compiler handles reference linking for incremental compilation.  Changes
    //      to the dependency hint class will trigger recompilation of the module that references it.
    session.dependenciesSymbolProvider.symbolNamesProvider
      .getTopLevelClassifierNamesInPackage(FqNames.anvilHintPackage)
      .orEmpty()
      .map { ClassId(FqNames.anvilHintPackage, it).requireRegularClassSymbol(session) }
  }

  public val allDependencyContributedModules: List<ContributedModule> by dependencyHintClassSymbols.map { allHintSymbols ->
    allHintSymbols.flatMap { clazzSymbol ->

      val annotation =
        clazzSymbol.getAnnotationByClassId(ClassIds.anvilInternalContributedModuleHints, session)
          ?: return@flatMap emptyList()

      annotation.requireArgumentAt(
        name = Names.hints,
        index = 1,
        unwrapNamedArguments = true,
      ).unwrapVarargValue()
        .map { expression ->
          val hint = expression.evaluateAs<FirLiteralExpression>(session)
            .sure { "can't evaluate expression: $expression" }.value as String

          val allNames = hint.split('|')
            .map { ClassId.topLevel(FqName(it.trim())) }

          checkWithAttachment(
            allNames.size >= 2,
            {
              "Expected at least two names in contributed module hint"
            },
          ) {
            withFirEntry("annotation", annotation)
            withEntry("contributed module hint", hint)
          }

          ContributedModule(
            scopeType = cachesFactory.createLazyValue { allNames[0] },
            contributedType = allNames[1],
            replaces = cachesFactory.createLazyValue { allNames.drop(2) },
          )
        }
    }
  }

  public val allDependencyContributedComponents: List<ContributedSupertype>
    by dependencyHintClassSymbols.map { allHintSymbols ->
      allHintSymbols.flatMap { clazzSymbol ->

        val annotation =
          clazzSymbol.getAnnotationByClassId(
            ClassIds.anvilInternalContributedComponentHints,
            session,
          )
            ?: return@flatMap emptyList()

        annotation.requireArgumentAt(
          name = Names.hints,
          index = 1,
          unwrapNamedArguments = true,
        ).unwrapVarargValue()
          .map { expression ->
            val hint = expression.evaluateAs<FirLiteralExpression>(session)
              .sure { "can't evaluate expression: $expression" }.value as String

            val allNames = hint.split('|')
              .map { ClassId.topLevel(FqName(it.trim())) }

            checkWithAttachment(
              allNames.size >= 2,
              {
                "Expected at least two names in contributed component hint"
              },
            ) {
              withFirEntry("annotation", annotation)
              withEntry("contributed component hint", hint)
            }

            ContributedSupertype(
              scopeType = cachesFactory.createLazyValue { allNames[0] },
              contributedType = allNames[1],
              replaces = cachesFactory.createLazyValue { allNames.drop(2) },
            )
          }
      }
    }

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    register(AnvilPredicates.hasAnvilInternalContributedModuleHints)
    register(AnvilPredicates.hasAnvilInternalContributedComponentHints)
  }
}
