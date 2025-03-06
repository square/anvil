package com.squareup.anvil.compiler.k2.fir.merging

import com.google.auto.service.AutoService
import com.squareup.anvil.compiler.k2.fir.AnvilFirContext
import com.squareup.anvil.compiler.k2.fir.AnvilFirDeclarationGenerationExtension
import com.squareup.anvil.compiler.k2.fir.AnvilFirExtensionFactory
import com.squareup.anvil.compiler.k2.fir.contributions.ContributedModule
import com.squareup.anvil.compiler.k2.fir.contributions.GeneratedBindingHintKey
import com.squareup.anvil.compiler.k2.fir.contributions.anvilFirScopedContributionProvider
import com.squareup.anvil.compiler.k2.fir.contributions.wrapInSyntheticFile
import com.squareup.anvil.compiler.k2.utils.fir.AnvilPredicates
import com.squareup.anvil.compiler.k2.utils.fir.createFirAnnotation
import com.squareup.anvil.compiler.k2.utils.fir.requireClassLikeSymbol
import com.squareup.anvil.compiler.k2.utils.fir.toGetClassCall
import com.squareup.anvil.compiler.k2.utils.names.ClassIds
import com.squareup.anvil.compiler.k2.utils.names.FqNames
import com.squareup.anvil.compiler.k2.utils.names.Names
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotationArgumentMapping
import org.jetbrains.kotlin.fir.expressions.builder.buildArgumentList
import org.jetbrains.kotlin.fir.expressions.builder.buildArrayLiteral
import org.jetbrains.kotlin.fir.expressions.builder.buildLiteralExpression
import org.jetbrains.kotlin.fir.extensions.ExperimentalTopLevelDeclarationsGenerationApi
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.plugin.createTopLevelClass
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.types.createArrayType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.checkers.OptInNames
import org.jetbrains.kotlin.types.ConstantValueKind
import java.security.MessageDigest

public class AnvilFirContributesModuleExtension(
  anvilFirContext: AnvilFirContext,
  session: FirSession,
) : AnvilFirDeclarationGenerationExtension(anvilFirContext, session) {

  private val contributedModules by lazy {
    session.anvilFirScopedContributionProvider.getContributions()
      .filterIsInstance<ContributedModule>()
  }

  @AutoService(AnvilFirExtensionFactory::class)
  public class Factory : AnvilFirDeclarationGenerationExtension.Factory {
    override fun create(
      anvilFirContext: AnvilFirContext,
    ): FirDeclarationGenerationExtension.Factory {
      return FirDeclarationGenerationExtension.Factory { session ->
        AnvilFirContributesModuleExtension(anvilFirContext, session)
      }
    }
  }

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    register(AnvilPredicates.hasAnyAnvilContributes)
  }

  override fun hasPackage(packageFqName: FqName): Boolean {
    return packageFqName == FqNames.anvilHintPackage && contributedModules.isNotEmpty()
  }

  @ExperimentalTopLevelDeclarationsGenerationApi
  override fun generateTopLevelClassLikeDeclaration(classId: ClassId): FirClassLikeSymbol<*> {

    return createTopLevelClass(
      classId = classId,
      key = GeneratedBindingHintKey,
      classKind = ClassKind.INTERFACE,
    ) {
      visibility = Visibilities.Private
    }
      .apply {
        val newAnnotations = listOf(
          // creates `@OptIn(AnvilInternalContributedModule::class)`
          createFirAnnotation(
            type = OptInNames.OPT_IN_CLASS_ID,
            argumentMapping = buildAnnotationArgumentMapping {
              mapping[OptInNames.OPT_IN_ANNOTATION_CLASS] =
                ClassIds.anvilInternalContributedModule.requireClassLikeSymbol(session).toGetClassCall()
            },
          ),
          // creates `@AnvilInternalContributedModule(hints = ["scope|contributedType|replaces"])`
          createFirAnnotation(
            type = ClassIds.anvilInternalContributedModule,
            argumentMapping = buildAnnotationArgumentMapping {

              mapping[Names.hints] = buildArrayLiteral {
                coneTypeOrNull = session.builtinTypes.stringType.coneType.createArrayType()
                argumentList = buildArgumentList {
                  contributedModules.mapTo(arguments) { module ->
                    buildLiteralExpression(
                      source = null,
                      kind = ConstantValueKind.String,
                      value = listOf(
                        module.scopeType,
                        module.contributedType,
                        *module.replaces.toTypedArray<ClassId>(),
                      ).joinToString("|") { it.asFqNameString() },
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

        replaceAnnotations(newAnnotations)
      }
      .wrapInSyntheticFile(session).symbol
  }

  private val HASH_STRING_LENGTH = 8

  private val MAX_FILE_NAME_LENGTH = 255
    .minus(14) // ".kapt_metadata" is the longest extension
    .minus(8) // "Provider" is the longest suffix that Dagger might add

  private fun md5Hash(params: List<Any>): String {
    return MessageDigest.getInstance("MD5")
      .apply {
        params.forEach {
          update(it.toString().toByteArray())
        }
      }
      .digest()
      .take(HASH_STRING_LENGTH / 2)
      .joinToString("") { "%02x".format(it) }
  }

  @ExperimentalTopLevelDeclarationsGenerationApi
  override fun getTopLevelClassIds(): Set<ClassId> {

    val contributedModuleSymbols = session.anvilFirScopedContributionProvider
      .contributedModuleSymbols
    val contributedBindings = session.anvilFirScopedContributionProvider
      .contributesBindingSymbols
    return if (contributedModuleSymbols.isNotEmpty() || contributedBindings.isNotEmpty()) {
      val moduleHash = md5Hash(contributedModuleSymbols.map { it.classId })
      setOf(
        ClassId(
          FqNames.anvilHintPackage,
          Name.identifier("AnvilContributedModules_$moduleHash"),
        ),
      )
    } else {
      emptySet()
    }
  }
}
