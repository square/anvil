package com.squareup.anvil.compiler.k2.fir.abstraction

import com.squareup.anvil.compiler.k2.fir.AnvilFirContext
import com.squareup.anvil.compiler.k2.fir.contributions.ContributedModule
import com.squareup.anvil.compiler.k2.fir.contributions.GeneratedBindingHintKey
import com.squareup.anvil.compiler.k2.utils.fir.createFirAnnotation
import com.squareup.anvil.compiler.k2.utils.fir.requireClassLikeSymbol
import com.squareup.anvil.compiler.k2.utils.fir.toGetClassCall
import com.squareup.anvil.compiler.k2.utils.names.ClassIds
import com.squareup.anvil.compiler.k2.utils.names.FqNames
import com.squareup.anvil.compiler.k2.utils.names.Names
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.caches.firCachesFactory
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotationArgumentMapping
import org.jetbrains.kotlin.fir.expressions.builder.buildArgumentList
import org.jetbrains.kotlin.fir.expressions.builder.buildArrayLiteral
import org.jetbrains.kotlin.fir.expressions.builder.buildLiteralExpression
import org.jetbrains.kotlin.fir.extensions.FirExtension
import org.jetbrains.kotlin.fir.types.createArrayType
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.checkers.OptInNames
import org.jetbrains.kotlin.types.ConstantValueKind

internal class AnvilContributedModulesGenerator(
  private val anvilFirContext: AnvilFirContext,
  private val session: FirSession,
) {

  fun doThings(
    contributedModules: List<ContributedModule>,
    firExtension: FirExtension,
  ): List<PendingTopLevelProperty> = contributedModules.groupBy { it.scopeType }
    .map { (_, modules) ->

      PendingTopLevelProperty(
        callableId = CallableId(FqNames.anvilHintPackage, Name.identifier("my_hints")),
        key = GeneratedBindingHintKey,
        visibility = Visibilities.Private,
        annotations = session.firCachesFactory.createLazyValue {

          val sortedHints = modules.map { module ->
            listOf(
              module.scopeType.getValue(),
              module.contributedType,
              *module.replaces.getValue().toTypedArray<ClassId>(),
            ).joinToString("|") { it.asFqNameString() }
          }
            .sorted()

          listOf(
            createFirAnnotation(
              type = OptInNames.OPT_IN_CLASS_ID,
              argumentMapping = buildAnnotationArgumentMapping {
                mapping[OptInNames.OPT_IN_ANNOTATION_CLASS] =
                  ClassIds.anvilInternalAnvilApi.requireClassLikeSymbol(session).toGetClassCall()
              },
            ),
            createFirAnnotation(
              type = ClassIds.anvilInternalContributedModuleHints,
              argumentMapping = buildAnnotationArgumentMapping {
                mapping[Names.hints] = buildArrayLiteral {
                  coneTypeOrNull = session.builtinTypes.stringType.coneType.createArrayType()
                  argumentList = buildArgumentList {
                    arguments += sortedHints.map { hint ->
                      buildLiteralExpression(
                        source = null,
                        kind = ConstantValueKind.String,
                        value = hint,
                        annotations = null,
                        setType = true,
                        prefix = null,
                      )
                    }
                  }
                }
              },
            ),
          )
        },
        cachesFactory = session.firCachesFactory,
        firExtension = firExtension,
      )
    }
}
