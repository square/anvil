package com.squareup.anvil.compiler.k2.fir.merging

import com.google.auto.service.AutoService
import com.squareup.anvil.compiler.k2.fir.AnvilFirContext
import com.squareup.anvil.compiler.k2.fir.AnvilFirExtension
import com.squareup.anvil.compiler.k2.fir.AnvilFirExtensionFactory
import com.squareup.anvil.compiler.k2.fir.AnvilFirSupertypeGenerationExtension
import com.squareup.anvil.compiler.k2.fir.internal.AnvilPredicates
import com.squareup.anvil.compiler.k2.fir.internal.classId
import com.squareup.anvil.compiler.k2.utils.fir.argumentAt
import com.squareup.anvil.compiler.k2.utils.fir.classListArgumentAt
import com.squareup.anvil.compiler.k2.utils.fir.contributesToScope
import com.squareup.anvil.compiler.k2.utils.fir.fqName
import com.squareup.anvil.compiler.k2.utils.fir.ktPsiFactory
import com.squareup.anvil.compiler.k2.utils.fir.requireFqName
import com.squareup.anvil.compiler.k2.utils.fir.requireScopeArgument
import com.squareup.anvil.compiler.k2.utils.fir.resolveConeType
import com.squareup.anvil.compiler.k2.utils.fir.setAnnotationType
import com.squareup.anvil.compiler.k2.utils.fir.toGetClassCall
import com.squareup.anvil.compiler.k2.utils.names.ClassIds
import com.squareup.anvil.compiler.k2.utils.names.FqNames
import com.squareup.anvil.compiler.k2.utils.names.Names
import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.fakeElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirClassLikeDeclaration
import org.jetbrains.kotlin.fir.declarations.utils.classId
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotationCallCopy
import org.jetbrains.kotlin.fir.expressions.builder.buildArgumentList
import org.jetbrains.kotlin.fir.expressions.builder.buildArrayLiteral
import org.jetbrains.kotlin.fir.expressions.builder.buildNamedArgumentExpression
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.FirSupertypeGenerationExtension
import org.jetbrains.kotlin.fir.extensions.predicate.DeclarationPredicate
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.psi
import org.jetbrains.kotlin.fir.resolve.fqName
import org.jetbrains.kotlin.fir.resolve.providers.FirCachedSymbolNamesProvider
import org.jetbrains.kotlin.fir.resolve.providers.dependenciesSymbolProvider
import org.jetbrains.kotlin.fir.resolve.providers.firProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtCollectionLiteralExpression
import org.jetbrains.kotlin.toKtPsiSourceElement

public fun AnvilFirExtension.logInfo(message: String) {
  anvilFirContext.messageCollector.report(CompilerMessageSeverity.INFO, message)
}

private fun Collection<*>?.lines(): String {
  return this?.map { it.toString() }
    ?.sorted()
    ?.joinToString("\n")
    ?: "null"
}

public fun AnvilFirExtension.printThings(vararg things: Pair<String, Any?>) {

  logInfo(
    things.joinToString(
      separator = "\n*****\n",
      prefix = "*****\n",
      postfix = "\n*****",
    ) { (name, thing) ->
      val ts = thing.toString().lines()
      """
      |${name.padStart(50, ' ')}  --  ${ts.first()}
      |${ts.drop(1).joinToString("\n") { " ".repeat(56) + it }}
      """.trimMargin()
    },
  )
}

public fun AnvilFirExtension.printThing(name: String, thing: Any?) {
  val ts = thing.toString().lines()
  logInfo(
    """
    |************************************************************
    |${name.padStart(50, ' ')}  --  ${ts.first()}
    |${ts.drop(1).joinToString("\n") { " ".repeat(56) + it }}
    |************************************************************
    """.trimMargin(),
  )
}

/**
 * This extension merges all contributed Dagger modules on the classpath and includes them on the
 * component annotated with `@MergeComponent`.
 */
public class AnvilFirAnnotationMergingExtension(
  anvilFirContext: AnvilFirContext,
  session: FirSession,
) : AnvilFirSupertypeGenerationExtension(anvilFirContext, session) {

  @AutoService(AnvilFirExtensionFactory::class)
  public class Factory : AnvilFirSupertypeGenerationExtension.Factory {
    override fun create(anvilFirContext: AnvilFirContext): FirSupertypeGenerationExtension.Factory {
      return FirSupertypeGenerationExtension.Factory { session ->
        AnvilFirAnnotationMergingExtension(anvilFirContext, session)
      }
    }
  }

  private companion object {
    private val PREDICATE = DeclarationPredicate.create {
      annotated(ClassIds.anvilMergeComponent.asSingleFqName())
    }
  }

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    register(AnvilPredicates.hasMergeComponentAnnotation)
    register(AnvilPredicates.hasInjectAnnotation)
    register(AnvilPredicates.contributedModule)
  }

  override fun computeAdditionalSupertypes(
    classLikeDeclaration: FirClassLikeDeclaration,
    resolvedSupertypes: List<FirResolvedTypeRef>,
    typeResolver: TypeResolveService,
  ): List<ConeKotlinType> {

    val hintsPackage = FqNames.hintsPackage

    val depsSymbolNamesProvider = session.dependenciesSymbolProvider.symbolNamesProvider

    val md = session.moduleData

    val computed = (depsSymbolNamesProvider as? FirCachedSymbolNamesProvider)
      ?.computeTopLevelClassifierNames(hintsPackage)
      .orEmpty()
      .mapNotNull {
        session.dependenciesSymbolProvider.getClassLikeSymbolByClassId(ClassId(hintsPackage, it))
      }

    printThings(
      "deps computed names" to computed.lines(),
      "fir class names in package" to session.firProvider.getClassNamesInPackage(hintsPackage)
        .lines(),
      "deps package names" to depsSymbolNamesProvider.getPackageNames().lines(),
      // "deps package names with top level classifiers" to depsSymbolNamesProvider.getPackageNamesWithTopLevelClassifiers().lines(),
      // "deps package names with top level callables" to depsSymbolNamesProvider.getPackageNamesWithTopLevelCallables().lines(),
      "deps hints" to
        depsSymbolNamesProvider.getTopLevelCallableNamesInPackage(hintsPackage)
          .orEmpty()
          .flatMap { propertyName ->
            session.dependenciesSymbolProvider.getTopLevelCallableSymbols(
              hintsPackage,
              propertyName,
            )
          }
          .map { callable -> callable.annotations.map { it.classId(session) } }
          .lines(),
    )

    val file = session.firProvider.getFirClassifierContainerFile(classLikeDeclaration.classId)

    // printThing("fir file", FirTreePrinter().treeString(file))

    // (session.firProvider as FirProviderImpl)
    //
    // file.transformSingle(
    //   object : FirDefaultTransformer<Nothing?>() {
    //     override fun <E : FirElement> transformElement(element: E, data: Nothing?): E {
    //       logInfo("element: ${element.javaClass.simpleName}")
    //       return element
    //     }
    //   },
    //   null,
    // )
    //
    // printThing(
    //   name = "local inject",
    //   thing = session.predicateBasedProvider
    //     .getSymbolsByPredicate(AnvilPredicates.hasInjectAnnotation),
    // )
    // printThing(
    //   name = "deps inject",
    //   thing = depSession.predicateBasedProvider
    //     .getSymbolsByPredicate(AnvilPredicates.hasInjectAnnotation),
    // )
    //
    // printThing(
    //   name = "@Module modules",
    //   thing = session.predicateBasedProvider
    //     .getSymbolsByPredicate(AnvilPredicates.contributedModule)
    //     .plus(fromDeps),
    // )
    //
    // printThing(
    //   name = "dependencies getPackageNamesWithTopLevelClassifiers",
    //   thing = session.dependenciesSymbolProvider.symbolNamesProvider
    //     .getPackageNamesWithTopLevelClassifiers()
    //     .orEmpty()
    //     .joinToString("\n"),
    // )

    // printThing(
    //   name = "deps package InjectClass",
    //   thing = session.dependenciesSymbolProvider
    //     .getClassLikeSymbolByClassId(ClassId.topLevel(FqName("com.squareup.test.dep.InjectClass"))),
    // )

    val componentAnnotation = classLikeDeclaration.annotations
      .single { it.fqName(session) == ClassIds.anvilMergeComponent.asSingleFqName() } as FirAnnotationCall

    val scope = componentAnnotation.requireScopeArgument().resolveConeType(typeResolver)
    val scopeFqName = scope.requireFqName()

    val oldModules = componentAnnotation
      .classListArgumentAt(Names.modules, index = 1)
      .orEmpty()

    classLikeDeclaration.replaceAnnotations(
      classLikeDeclaration.annotations + buildAnnotationCallCopy(componentAnnotation) {
        setAnnotationType(
          newType = ClassIds.daggerComponent.asSingleFqName(),
          ktPsiFactoryOrNull = classLikeDeclaration.psi?.ktPsiFactory(),
        )

        val contributedModules = session.predicateBasedProvider
          .getSymbolsByPredicate(AnvilPredicates.contributedModule)
          .mapNotNull { moduleClass ->
            if (moduleClass !is FirClassLikeSymbol<*>) return@mapNotNull null

            if (moduleClass.contributesToScope(
                scope = scopeFqName,
                session = session,
                typeResolveService = typeResolver,
              )
            ) {
              moduleClass
            } else {
              null
            }
          }

        val newAnnotationCallPsi = componentAnnotation.psi?.let { psi ->
          buildNewAnnotationPsi(
            oldAnnotationCall = psi as KtAnnotationEntry,
            mergedModules = contributedModules.map { it.fqName() },
          )
        }

        val newSource =
          newAnnotationCallPsi?.toKtPsiSourceElement(KtFakeSourceElementKind.PluginGenerated)
            ?: componentAnnotation.source?.fakeElement(KtFakeSourceElementKind.PluginGenerated)
        source = newSource

        argumentList = buildArgumentList {
          source = componentAnnotation.source?.fakeElement(KtFakeSourceElementKind.PluginGenerated)

          arguments += buildNamedArgumentExpression {
            name = Names.modules
            isSpread = false
            expression = buildArrayLiteral {
              argumentList = buildArgumentList {
                arguments += oldModules
                arguments += contributedModules.map { it.toGetClassCall() }
              }
            }
          }

          componentAnnotation.argumentAt(
            Names.dependencies,
            index = 2,
            unwrapNamedArguments = false,
          )?.let {
            arguments += it
          }
        }
      },
    )

    return listOf()
  }

  private fun buildNewAnnotationPsi(
    oldAnnotationCall: KtAnnotationEntry,
    mergedModules: List<FqName>,
  ): KtAnnotationEntry {

    val oldAnnotationArguments = oldAnnotationCall.valueArgumentList
      ?.arguments
      .orEmpty()

    // `modules = [SomeModule::class]`
    val oldModulesArg = oldAnnotationArguments
      // `Component` is a Java annotation with default argument values,
      // so its arguments can be missing or in any order, but they must be named if they're present.
      .firstOrNull { arg ->
        val name = arg.getArgumentName()
        name == null || name.text == "modules"
      }

    // `SomeModule::class, SomeOtherModule::class`
    val existingModuleArgExpressions =
      (oldModulesArg?.getArgumentExpression() as? KtCollectionLiteralExpression)
        ?.innerExpressions
        ?.map { it.text }
        .orEmpty()

    val imports = oldAnnotationCall.containingKtFile.importDirectives
      .associate { imp ->
        val fqName = imp.importedReference?.text
          ?: error("import directive doesn't have a reference? $imp")

        val name = imp.aliasName ?: imp.importedReference?.text?.substringAfterLast('.')
          ?: error("import directive doesn't have a reference or alias? ${imp.text}")

        fqName to name
      }

    val newModulesMaybeImported = mergedModules.map { it.asString() }
      .map { moduleFqName ->
        imports[moduleFqName] ?: moduleFqName
      }

    val allClassArgs = existingModuleArgExpressions
      .plus(newModulesMaybeImported.map { "$it::class" })
      .distinct()

    val factory = oldAnnotationCall.ktPsiFactory()

    val classArgList = allClassArgs.joinToString(separator = ", ")

    val newModulesText = "modules = [$classArgList]"

    val componentCall = ClassIds.daggerComponent.asString().let { fqString ->
      imports[fqString] ?: fqString
    }

    val newAnnotationText = when {
      oldAnnotationArguments.isEmpty() -> "@$componentCall($newModulesText)"
      oldModulesArg != null -> oldAnnotationCall.text.replace(oldModulesArg.text, newModulesText)
      else -> oldAnnotationArguments.map { it.text }
        .plus(newModulesText)
        .joinToString(
          separator = ",\n",
          prefix = "@$componentCall(\n",
          postfix = "\n)",
        ) { "  $it" }
    }

    return factory.createAnnotationEntry(newAnnotationText)
  }

  override fun needTransformSupertypes(declaration: FirClassLikeDeclaration): Boolean {
    return session.predicateBasedProvider.matches(PREDICATE, declaration)
  }
}
