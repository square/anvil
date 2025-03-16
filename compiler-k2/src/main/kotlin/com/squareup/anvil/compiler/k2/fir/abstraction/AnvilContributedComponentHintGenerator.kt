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
public class AnvilContributedComponentHintGeneratorFactory :
  AbstractAnvilFirProcessorFactory(::AnvilContributedComponentHintGenerator)

internal class AnvilContributedComponentHintGenerator(
  override val anvilFirContext: AnvilFirContext2,
) : TopLevelClassProcessor() {

  @OptIn(RequiresTypesResolutionPhase::class)
  private val contributedSupertypes by lazyValue {
    session.scopedContributionProvider.contributedSupertypes
  }

  override fun hasPackage(packageFqName: FqName): Boolean {
    return packageFqName == FqNames.anvilHintPackage &&
      session.anvilFirSymbolProvider.contributesSupertypeSymbols.isNotEmpty()
  }

  @ExperimentalTopLevelDeclarationsGenerationApi
  override fun getTopLevelClassIds(): Set<ClassId> {
    return if (contributedSupertypes.isEmpty()) {
      emptySet()
    } else {
      setOf(ClassIds.anvilContributedComponents(contributedSupertypes.map { it.contributedType }))
    }
  }

  @ExperimentalTopLevelDeclarationsGenerationApi
  override fun generateTopLevelClassLikeDeclaration(
    classId: ClassId,
    firExtension: FirExtension,
  ): PendingTopLevelClass = PendingTopLevelClass(
    classId = classId,
    key = GeneratedBindingHintKey,
    classKind = ClassKind.INTERFACE,
    visibility = Visibilities.Private,
    annotations = lazyValue {
      val sortedHints = contributedSupertypes.map { component ->
        listOf(
          component.scopeType.getValue(),
          component.contributedType,
          *component.replaces.getValue().toTypedArray<ClassId>(),
        ).joinToString("|") { it.asFqNameString() }
      }
        .sorted()

      listOf(
        createOptInAnnotation(ClassIds.anvilInternalAnvilApi, session),
        createFirAnnotation(
          type = ClassIds.anvilInternalContributedComponentHints,
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
