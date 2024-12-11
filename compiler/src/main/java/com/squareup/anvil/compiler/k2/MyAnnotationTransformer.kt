package com.squareup.anvil.compiler.k2

import com.squareup.anvil.compiler.internal.ktFile
import com.squareup.anvil.compiler.k2.internal.Names
import com.squareup.anvil.compiler.k2.internal.tree.AbstractTreePrinter
import com.squareup.anvil.compiler.k2.internal.tree.AbstractTreePrinter.Color.Companion.colorized
import com.squareup.anvil.compiler.k2.internal.tree.PsiTreePrinter.Companion.printEverything
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.expressions.FirArgumentList
import org.jetbrains.kotlin.fir.expressions.FirArrayLiteral
import org.jetbrains.kotlin.fir.expressions.FirClassReferenceExpression
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirLazyExpression
import org.jetbrains.kotlin.fir.expressions.FirStatement
import org.jetbrains.kotlin.fir.expressions.UnresolvedExpressionTypeAccess
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotationCallCopy
import org.jetbrains.kotlin.fir.expressions.builder.buildArgumentList
import org.jetbrains.kotlin.fir.expressions.builder.buildArrayLiteral
import org.jetbrains.kotlin.fir.expressions.builder.buildClassReferenceExpression
import org.jetbrains.kotlin.fir.extensions.FirSupertypeGenerationExtension.TypeResolveService
import org.jetbrains.kotlin.fir.extensions.buildUserTypeFromQualifierParts
import org.jetbrains.kotlin.fir.psi
import org.jetbrains.kotlin.fir.render
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.FirImplicitTypeRef
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.FirTypeRefWithNullability
import org.jetbrains.kotlin.fir.types.FirUserTypeRef
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.visitors.FirDefaultTransformer
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClassLiteralExpression
import org.jetbrains.kotlin.psi.KtCollectionLiteralExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.text

internal class MyAnnotationTransformer(
  private val session: FirSession,
  private val typeResolver: TypeResolveService,
  mergedModules: () -> List<FirRegularClassSymbol>,
) : FirDefaultTransformer<Unit>() {
  private val mergedModules: List<FirRegularClassSymbol> by lazy(mergedModules)

  override fun <E : FirElement> transformElement(element: E, data: Unit): E {
    return element
  }

  override fun transformAnnotation(annotation: FirAnnotation, data: Unit): FirStatement {
    error("transformAnnotation -- $annotation")
    return annotation
    return super.transformAnnotation(annotation, data)
  }

  @OptIn(UnresolvedExpressionTypeAccess::class)
  override fun transformAnnotationCall(
    annotationCall: FirAnnotationCall,
    data: Unit,
  ): FirStatement {

    // typeResolver.resolveUserType(annotationCall.annotationTypeRef)

    val annotationFqName = when (val tr = annotationCall.annotationTypeRef) {
      is FirUserTypeRef -> typeResolver.resolveUserType(tr).coneType
      is FirResolvedTypeRef -> tr.coneType
      is FirImplicitTypeRef -> tr.coneType
      is FirTypeRefWithNullability -> tr.coneType
    }.classId!!.asSingleFqName()

    println("@@@@@@@@@@@@@@ annotationFqName -- $annotationFqName")

    if (annotationFqName !in setOf(Names.dagger.component, Names.componentKotlin)) {
      return super.transformAnnotationCall(annotationCall, data)
    }

    val annotationArgsListOld: FirArgumentList = annotationCall.argumentList

    // annotationArgsListOld.transformArguments(NamedArgumentTransformer(), null)

    val oldModules = annotationArgsListOld.arguments
      // .filterIsInstance<FirNamedArgumentExpression>()
      .singleOrNull() // { it.name == "modules".nameAsSafeName() }
      .let { expression ->
        requireNotNull(expression) {

          val args = annotationArgsListOld.arguments.filterIsInstance<FirLazyExpression>()

          """
            |~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            |${args.joinToString("\n") { it.source?.text ?: "null" }}
            |~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          """.trimMargin()
        }
      }

//    return buildAnnotationCopy(annotationCall) {
//      buildArgumentList {
//
//        val oldClassListArg = annotationArgsListOld.arguments.single()
//          .acceptChildren(
//            object : FirVisitorVoid() {
//              override fun visitElement(element: FirElement) {
//                element.acceptChildren(this)
//              }
//
//              override fun visitVarargArgumentsExpression(
//                varargArgumentsExpression: FirVarargArgumentsExpression,
//              ) {
//                println("visitVarargArgumentsExpression -- $varargArgumentsExpression")
//                super.visitVarargArgumentsExpression(varargArgumentsExpression)
//              }
//
//              override fun visitArgumentList(argumentList: FirArgumentList) {
//                println("visitArgumentList -- $argumentList")
//                super.visitArgumentList(argumentList)
//              }
//            },
//          )
//
//        source = oldModules.source?.fakeElement(KtFakeSourceElementKind.PluginGenerated)
//        // arguments += oldModules.arguments
//
//        arguments += mergedModules.map { moduleSymbol ->
//          buildGetClassClass(moduleSymbol)
//        }
//      }
//    }

    // class Butt : FirTransformer<Nothing?>() {
    //   override fun <E : FirElement> transformElement(element: E, data: Nothing?): E {
    //     if (element is FirArrayLiteral) return element
    //     error("transformElement -- $element  --  ${element.source?.text}")
    //   }
    // }
    // oldModules.transformChildren(Butt(), null)

    // buildAnnotationCall {
    //   buildArgumentList {
    //     source = oldArrayArgs.source?.fakeElement(KtFakeSourceElementKind.PluginGenerated)
    //     arguments += oldArrayArgs.arguments
    //
    //     arguments += mergedModules.map { moduleSymbol ->
    //       buildGetClassClass(moduleSymbol)
    //     }
    //   },
    // }

    fun KtPsiFactory.createKClassValueArguments(
      moduleClassArgs: List<String>,
    ): KtValueArgumentList {
      return createCallArguments(
        moduleClassArgs.joinToString(separator = ", ", prefix = "(", postfix = ")"),
      )
    }

    fun KtPsiFactory.createModuleClassRefPsi(moduleFqName: FqName): KtClassLiteralExpression =
      createKClassValueArguments(listOf("${moduleFqName.asString()}::class"))
        .arguments[0]
        .firstChild as KtClassLiteralExpression

    fun createModuleClassRefExpression(
      moduleFqName: FqName,
    ): FirClassReferenceExpression = buildClassReferenceExpression {
      classTypeRef = buildUserTypeFromQualifierParts(false) {
        moduleFqName.pathSegments().forEach(::part)
      }
      // TODO: I don't think we need the cone type for anything?
      coneTypeOrNull = null
    }

    fun buildModulesArray(moduleFqNames: List<FqName>, psiFactory: KtPsiFactory): FirArrayLiteral {
      return buildArrayLiteral {
        argumentList = buildArgumentList argList@{
          this@argList.arguments += moduleFqNames.map { moduleFqName ->
            createModuleClassRefExpression(moduleFqName)
          }
        }
      }
    }

    fun createModuleListClassArg(oldClassList: FirExpression): FirExpression {
      val classListArg = oldClassList

      // `modules = [SomeModule::class]`
      val modulesArg = classListArg.psi as? KtValueArgument
        ?: error("modules arg doesn't have a PSI element: $classListArg")

      // `[SomeModule::class]`
      val moduleClassArray =
        modulesArg.getArgumentExpression()!! as KtCollectionLiteralExpression

      // `SomeModule::class`, `foo.SomeOtherModule::class`, etc.
      val moduleArgExpressions = moduleClassArray.innerExpressions
        // .map { it.requireFqName(org.jetbrains.kotlin.types.error.ErrorModuleDescriptor) }
        .map { it.text }

      val imports = modulesArg.ktFile().importDirectives
        .associate { imp ->
          val fqName = imp.importedReference?.text
            ?: error("import directive doesn't have a reference? $imp")

          val name = imp.aliasName ?: imp.importedReference?.name
            ?: error("import directive doesn't have a reference or alias? $imp")

          fqName to name
        }

      val newModules = listOf(Names.emptyModule)

      val newModulesMaybeImported = newModules.map { moduleFqName ->
        imports[moduleFqName.asString()] ?: moduleFqName.asString()
      }

      val allClassArgs = moduleArgExpressions
        .plus(newModulesMaybeImported.map { "$it::class" })
        .distinct()

      TODO()
    }

    val newCall = buildAnnotationCallCopy(annotationCall) callBuilder@{

      val al1: FirArgumentList = annotationCall.argumentList

      val newArgs = annotationCall.argumentList.arguments
        .mapIndexed map@{ index, classListArg ->
          if (index != 0) return@map classListArg

          println("classListArg -- ${classListArg.render()}")

          // `modules = [SomeModule::class]`
          val modulesArg = classListArg.psi as? KtValueArgument
            ?: return@map classListArg
          // ?: error(
          //   """
          //   ~~~~~~~~~~~~~~~~~~~~~~~~~
          //   modules arg doesn't have a PSI element:
          //
          //        render: ${classListArg.render()}
          //         class: ${classListArg::class.qualifiedName}
          //        source: ${classListArg.source}
          //   source text: ${classListArg.source?.text}
          //   ~~~~~~~~~~~~~~~~~~~~~~~~~
          //   """.trimIndent(),
          // )

          // `[SomeModule::class]`
          val modulesListExpression =
            modulesArg.getArgumentExpression()!! as KtCollectionLiteralExpression

          // `SomeModule::class, SomeOtherModule::class`
          val moduleArgExpressions = modulesListExpression.innerExpressions
            // .map { it.requireFqName(org.jetbrains.kotlin.types.error.ErrorModuleDescriptor) }
            .map { it.text }

          val imports = modulesArg.ktFile().importDirectives
            .associate { imp ->
              val fqName = imp.importedReference?.text
                ?: error("import directive doesn't have a reference? $imp")

              val name = imp.aliasName ?: imp.importedReference?.text?.substringAfterLast('.')
                ?: error("import directive doesn't have a reference or alias? ${imp.text}")

              fqName to name
            }

          val newModules = listOf(Names.bBindingModule)

          val newModulesMaybeImported = newModules.map { moduleFqName ->
            imports[moduleFqName.asString()] ?: moduleFqName.asString()
          }

          val allClassArgs = moduleArgExpressions
            .plus(newModulesMaybeImported.map { "$it::class" })
            .distinct()

          println(
            """
            |******************************************** all class args
            |${allClassArgs.joinToString("\n")}
            |********************************************
            """.trimMargin(),
          )

          // val originalClassListPsi = modulesArg as? KtValueArgumentList
          //   ?: error("class list arg doesn't have a PSI element: ${classListArg.psi}")

          val factory = modulesArg.ktPsiFactory()

          // val originalClassArgText = originalClassListPsi.arguments.map { it.text }

          // val newModules = listOf(Names.bBindingModule)

          // val allClassArgs = originalClassArgText
          //   .plus(newModules.map { "${it.asString()}::class" })

          val classArgList = allClassArgs.joinToString(separator = ", ")

          val annotationEntry = factory.createAnnotationEntry(
            "@Component(modules = [$classArgList])",
          )

          // psi.project.extensionArea.registerExtensionPoint(Extensions.getRootArea(),TreeCopyHandler.EP_NAME,)

          val ktValueArgumentList =
            factory.createKClassValueArguments(moduleClassArgs = allClassArgs)

          // source = KtRealPsiSourceElement(modulesArg)

          val thing = factory.createArgument("foo.Bar::class")

          ktValueArgumentList.printEverything()

          println(
            """
              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
              
              ktValueArgumentList: ${ktValueArgumentList.text}
              
              thing: $thing
              thing: ${thing?.text}
              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            """.trimIndent().colorized(AbstractTreePrinter.Color.LIGHT_RED),
          )

          classListArg
        }

      val b = this@callBuilder

      val al = buildArgumentList { }

      // b.argumentList =   newArgs
    }

    println(
      """
      |%%%%%%%%%%%%%%%%%%%%
      |
      |${newCall.render()}
      |
      |%%%%%%%%%%%%%%%%%%%%
      """.trimMargin(),
    )

    // oldModules.transformChildren { element ->
    //
    //   println("transformChildren -- $element")
    //
    //   val oldModulesArray = element as? FirArrayLiteral
    //     ?: return@transformChildren element
    //
    //   val oldArrayArgs = oldModulesArray.argumentList
    //
    //   oldModulesArray.replaceArgumentList(
    //     buildArgumentList {
    //       source = oldArrayArgs.source?.fakeElement(KtFakeSourceElementKind.PluginGenerated)
    //       arguments += oldArrayArgs.arguments
    //
    //       arguments += mergedModules.map { moduleSymbol ->
    //         buildGetClassClass(moduleSymbol)
    //       }
    //     },
    //   )
    //
    //   oldModulesArray
    // }

    // (oldModules as FirNamedArgumentExpression).let { oldModulesExpression ->
    //   (oldModulesExpression.expression as FirArrayLiteral).let { oldModulesArray ->
    //     val oldArrayArgs = oldModulesArray.argumentList
    //
    //     oldModulesArray.replaceArgumentList(
    //       buildArgumentList {
    //         source = oldArrayArgs.source?.fakeElement(KtFakeSourceElementKind.PluginGenerated)
    //         arguments += oldArrayArgs.arguments
    //
    //         arguments += mergedModules.map { moduleSymbol ->
    //           buildGetClassClass(moduleSymbol)
    //         }
    //       },
    //     )
    //   }
    // }

    return super.transformAnnotationCall(newCall, data)
  }
}

private fun PsiElement.ktPsiFactory(): KtPsiFactory {
  return KtPsiFactory.contextual(
    context = this@ktPsiFactory,
    markGenerated = false,
    eventSystemEnabled = false,
  )
}
