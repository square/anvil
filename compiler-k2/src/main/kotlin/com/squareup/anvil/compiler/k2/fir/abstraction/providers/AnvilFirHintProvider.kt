package com.squareup.anvil.compiler.k2.fir.abstraction.providers

import com.google.auto.service.AutoService
import com.squareup.anvil.compiler.k2.fir.AnvilFirContext
import com.squareup.anvil.compiler.k2.fir.AnvilFirExtensionFactory
import com.squareup.anvil.compiler.k2.fir.AnvilFirExtensionSessionComponent
import com.squareup.anvil.compiler.k2.fir.contributions.ContributedModule
import com.squareup.anvil.compiler.k2.fir.contributions.ScopedContribution
import com.squareup.anvil.compiler.k2.utils.fir.AnvilPredicates
import com.squareup.anvil.compiler.k2.utils.fir.requireAnnotation
import com.squareup.anvil.compiler.k2.utils.fir.requireArgumentAt
import com.squareup.anvil.compiler.k2.utils.names.ClassIds
import com.squareup.anvil.compiler.k2.utils.names.Names
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.caches.firCachesFactory
import org.jetbrains.kotlin.fir.caches.getValue
import org.jetbrains.kotlin.fir.declarations.evaluateAs
import org.jetbrains.kotlin.fir.declarations.unwrapVarargValue
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.FirExtensionSessionComponent
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.utils.exceptions.withFirEntry
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.utils.exceptions.checkWithAttachment
import org.jetbrains.kotlin.utils.sure

public val FirSession.anvilFirHintProvider: AnvilFirHintProvider by FirSession.sessionComponentAccessor()

public class AnvilFirHintProvider(
  anvilFirContext: AnvilFirContext,
  session: FirSession,
) : AnvilFirExtensionSessionComponent(anvilFirContext, session) {

  private val cachesFactory = session.firCachesFactory

  private val dependencyHintClassSymbols by cachesFactory.createLazyValue {

    // session.dependenciesSymbolProvider
    //   .symbolNamesProvider.getTopLevelClassifierNamesInPackage(FqNames.anvilHintPackage)
    //   .orEmpty()
    //   .mapNotNull {
    //     session.dependenciesSymbolProvider
    //       .getClassLikeSymbolByClassId(ClassId(FqNames.anvilHintPackage, it))
    //   }

    emptyList<FirClassLikeSymbol<*>>()
  }

  private val allScopedContributions = cachesFactory.createLazyValue { parseDependencyHints() }

  public fun getContributions(): List<ScopedContribution> = allScopedContributions.getValue()

  private fun parseDependencyHints(): List<ScopedContribution> = dependencyHintClassSymbols
    .flatMap { clazzSymbol ->

      val annotation = clazzSymbol.requireAnnotation(
        classId = ClassIds.anvilInternalContributedModule,
        session = session,
      )

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

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    register(AnvilPredicates.contributedModule)
  }
}

@AutoService(AnvilFirExtensionFactory::class)
public class AnvilFirHintProviderFactory : AnvilFirExtensionSessionComponent.Factory {
  override fun create(anvilFirContext: AnvilFirContext): FirExtensionSessionComponent.Factory {
    return FirExtensionSessionComponent.Factory { session ->
      AnvilFirHintProvider(anvilFirContext, session)
    }
  }
}
