package com.squareup.anvil.compiler.k2.fir.abstraction

import com.google.auto.service.AutoService
import com.squareup.anvil.compiler.k2.fir.AbstractAnvilFirProcessorFactory
import com.squareup.anvil.compiler.k2.fir.AnvilFirContext2
import com.squareup.anvil.compiler.k2.fir.AnvilFirProcessor
import com.squareup.anvil.compiler.k2.fir.PendingTopLevelClass
import com.squareup.anvil.compiler.k2.fir.RequiresTypesResolutionPhase
import com.squareup.anvil.compiler.k2.fir.TopLevelClassProcessor
import com.squareup.anvil.compiler.k2.fir.abstraction.providers.anvilFirSymbolProvider
import com.squareup.anvil.compiler.k2.fir.abstraction.providers.scopedContributionProvider
import com.squareup.anvil.compiler.k2.fir.contributions.GeneratedBindingHintKey
import com.squareup.anvil.compiler.k2.utils.fir.createFirAnnotation
import com.squareup.anvil.compiler.k2.utils.fir.createOptInAnnotation
import com.squareup.anvil.compiler.k2.utils.names.ClassIds
import com.squareup.anvil.compiler.k2.utils.names.FqNames
import com.squareup.anvil.compiler.k2.utils.names.Names
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.caches.firCachesFactory
import org.jetbrains.kotlin.fir.caches.getValue
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotationArgumentMapping
import org.jetbrains.kotlin.fir.expressions.builder.buildArgumentList
import org.jetbrains.kotlin.fir.expressions.builder.buildArrayLiteral
import org.jetbrains.kotlin.fir.expressions.builder.buildLiteralExpression
import org.jetbrains.kotlin.fir.extensions.ExperimentalTopLevelDeclarationsGenerationApi
import org.jetbrains.kotlin.fir.extensions.FirExtension
import org.jetbrains.kotlin.fir.types.createArrayType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.types.ConstantValueKind

@AutoService(AnvilFirProcessor.Factory::class)
public class AnvilContributedModuleHintGeneratorFactory :
  AbstractAnvilFirProcessorFactory(::AnvilContributedModuleHintGenerator)

internal class AnvilContributedModuleHintGenerator(
  override val anvilFirContext: AnvilFirContext2,
) : TopLevelClassProcessor() {

  private val contributedModuleSymbols by lazyValue {
    session.anvilFirSymbolProvider.contributesModulesSymbols
  }

  @OptIn(RequiresTypesResolutionPhase::class)
  private val contributedModules by lazyValue {
    session.scopedContributionProvider.contributedModules + session.scopedContributionProvider.contributedBindingModules
  }

  private val hintClassIdsToContributedModules by lazyValue {
    contributedModules.groupBy { it.contributedType }.entries
      .associate { (_, modules) ->

        val hintClassId = ClassIds.anvilContributedModules(modules.map { it.contributedType })

        hintClassId to modules
      }
  }

  override fun hasPackage(packageFqName: FqName): Boolean {
    return packageFqName == FqNames.anvilHintPackage && contributedModuleSymbols.isNotEmpty()
  }

  @ExperimentalTopLevelDeclarationsGenerationApi
  override fun getTopLevelClassIds(): Set<ClassId> = hintClassIdsToContributedModules.keys

  @ExperimentalTopLevelDeclarationsGenerationApi
  override fun generateTopLevelClassLikeDeclaration(
    classId: ClassId,
    firExtension: FirExtension,
  ): PendingTopLevelClass {
    return hintClassIdsToContributedModules.getValue(classId).let { modules ->
      PendingTopLevelClass(
        classId = classId,
        key = GeneratedBindingHintKey,
        classKind = ClassKind.INTERFACE,
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
            createOptInAnnotation(ClassIds.anvilInternalAnvilApi, session),
            createFirAnnotation(
              type = ClassIds.anvilInternalContributedModuleHints,
              argumentMapping = buildAnnotationArgumentMapping {
                mapping[Names.hints] = buildArrayLiteral {
                  coneTypeOrNull = session.builtinTypes.stringType.coneType.createArrayType()
                  argumentList = buildArgumentList {
                    sortedHints.mapTo(arguments) { hint ->
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
}
