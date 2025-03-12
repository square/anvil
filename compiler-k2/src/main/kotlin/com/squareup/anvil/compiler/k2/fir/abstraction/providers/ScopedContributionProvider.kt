package com.squareup.anvil.compiler.k2.fir.abstraction.providers

import com.google.auto.service.AutoService
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.compiler.k2.fir.AnvilFirContext
import com.squareup.anvil.compiler.k2.fir.AnvilFirExtensionFactory
import com.squareup.anvil.compiler.k2.fir.AnvilFirExtensionSessionComponent
import com.squareup.anvil.compiler.k2.fir.contributions.ContributedBinding
import com.squareup.anvil.compiler.k2.fir.contributions.ContributedModule
import com.squareup.anvil.compiler.k2.fir.contributions.ContributedSupertype
import com.squareup.anvil.compiler.k2.utils.fir.boundTypeArgumentOrNull
import com.squareup.anvil.compiler.k2.utils.fir.contributesToAnnotations
import com.squareup.anvil.compiler.k2.utils.fir.getContributesBindingAnnotations
import com.squareup.anvil.compiler.k2.utils.fir.rankArgumentOrNull
import com.squareup.anvil.compiler.k2.utils.fir.replacesArgumentOrNull
import com.squareup.anvil.compiler.k2.utils.fir.requireClassId
import com.squareup.anvil.compiler.k2.utils.fir.requireScopeArgument
import com.squareup.anvil.compiler.k2.utils.fir.requireTargetClassId
import com.squareup.anvil.compiler.k2.utils.fir.resolveConeType
import com.squareup.anvil.compiler.k2.utils.names.bindingModuleSibling
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.caches.getValue
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.extensions.FirExtensionSessionComponent
import org.jetbrains.kotlin.fir.extensions.FirSupertypeGenerationExtension
import org.jetbrains.kotlin.fir.resolve.getSuperTypes
import org.jetbrains.kotlin.fir.utils.exceptions.withFirSymbolEntry
import org.jetbrains.kotlin.utils.exceptions.errorWithAttachment
import kotlin.properties.Delegates

@RequiresTypesResolutionPhase
public val FirSession.scopedContributionProvider: ScopedContributionProvider by FirSession.sessionComponentAccessor()

@RequiresTypesResolutionPhase
public class ScopedContributionProvider(
  anvilFirContext: AnvilFirContext,
  session: FirSession,
) : AnvilFirExtensionSessionComponent(anvilFirContext, session) {

  private var typeResolveService: FirSupertypeGenerationExtension.TypeResolveService by Delegates.notNull()

  public val contributedModules: List<ContributedModule> by cachedLazy {
    session.anvilFirSymbolProvider.contributesModulesSymbols.flatMap { symbol ->
      symbol.contributesToAnnotations(session).map { annotation ->

        ContributedModule(
          scopeType = cachedLazy {
            annotation.requireScopeArgument(typeResolveService).requireClassId()
          },
          contributedType = symbol.classId,
          replaces = cachedLazy { annotation.replacesClassIds() },
        )
      }
    }
  }
  public val contributedSupertypes: List<ContributedSupertype> by cachedLazy {
    session.anvilFirSymbolProvider.contributesSupertypeSymbols
      .flatMap { symbol ->
        symbol.contributesToAnnotations(session).map { annotation ->

          ContributedSupertype(
            scopeType = cachedLazy {
              annotation.requireScopeArgument(typeResolveService).requireClassId()
            },
            contributedType = symbol.classId,
            replaces = cachedLazy { annotation.replacesClassIds() },
          )
        }
      }
  }
  private val contributedBindingsAndBindingModules = cachedLazy {
    session.anvilFirSymbolProvider.contributesBindingSymbols.flatMap { symbol ->

      symbol.getContributesBindingAnnotations(session).flatMap { annotation ->

        val boundType = cachedLazy {
          annotation.boundTypeArgumentOrNull(session)
            ?.resolveConeType(typeResolveService)
            ?.requireClassId()
            ?: symbol.getSuperTypes(
              useSiteSession = session,
              recursive = false,
              lookupInterfaces = true,
            )
              .singleOrNull()
              ?.requireClassId()
            ?: errorWithAttachment("No supertype found for @ContributesBinding type") {
              withFirSymbolEntry("annotated class", symbol)
            }
        }

        val scopeType = cachedLazy {
          annotation.requireScopeArgument(typeResolveService).requireClassId()
        }
        val replaces = cachedLazy { annotation.replacesClassIds() }

        listOf(
          ContributedBinding(
            scopeType = scopeType,
            boundType = boundType,
            contributedType = symbol.classId,
            replaces = replaces,
            rank = annotation.rankArgumentOrNull(session) ?: ContributesBinding.RANK_NORMAL,
            ignoreQualifier = false,
            isMultibinding = false,
            bindingModule = symbol.classId.bindingModuleSibling,
            qualifier = null,
          ),
          ContributedModule(
            scopeType = scopeType,
            contributedType = symbol.classId.bindingModuleSibling,
            replaces = replaces,
          ),
        )
      }
    }
  }

  public val contributedBindings: List<ContributedBinding> by contributedBindingsAndBindingModules.map {
    it.filterIsInstance<ContributedBinding>()
  }
  public val contributedBindingModules: List<ContributedModule> by contributedBindingsAndBindingModules.map {
    it.filterIsInstance<ContributedModule>()
  }

  internal val contributesTo by cachedLazy {
  }
  private var typeResolverSet = false
  internal fun isInitialized() = typeResolverSet
  internal fun bindTypeResolveService(
    typeResolveService: FirSupertypeGenerationExtension.TypeResolveService,
  ) {
    typeResolverSet = true
    this.typeResolveService = typeResolveService
  }

  private fun FirAnnotationCall.replacesClassIds() = replacesArgumentOrNull(session)
    ?.map { it.requireTargetClassId() }
    .orEmpty()
}

@AutoService(AnvilFirExtensionFactory::class)
public class ScopedContributionProviderFactory : AnvilFirExtensionSessionComponent.Factory {
  @OptIn(RequiresTypesResolutionPhase::class)
  override fun create(anvilFirContext: AnvilFirContext): FirExtensionSessionComponent.Factory {
    return FirExtensionSessionComponent.Factory { session ->
      ScopedContributionProvider(anvilFirContext, session)
    }
  }
}
