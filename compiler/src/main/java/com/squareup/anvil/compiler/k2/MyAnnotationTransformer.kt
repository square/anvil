package com.squareup.anvil.compiler.k2

import com.squareup.anvil.compiler.k2.internal.Names
import com.squareup.anvil.compiler.k2.internal.buildGetClassClass
import com.squareup.anvil.compiler.k2.internal.transformChildren
import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.fakeElement
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.expressions.FirArgumentList
import org.jetbrains.kotlin.fir.expressions.FirArrayLiteral
import org.jetbrains.kotlin.fir.expressions.FirLazyExpression
import org.jetbrains.kotlin.fir.expressions.FirNamedArgumentExpression
import org.jetbrains.kotlin.fir.expressions.FirStatement
import org.jetbrains.kotlin.fir.expressions.UnresolvedExpressionTypeAccess
import org.jetbrains.kotlin.fir.expressions.builder.buildArgumentList
import org.jetbrains.kotlin.fir.extensions.FirSupertypeGenerationExtension.TypeResolveService
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.FirImplicitTypeRef
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.FirTypeRefWithNullability
import org.jetbrains.kotlin.fir.types.FirUserTypeRef
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.visitors.FirDefaultTransformer
import org.jetbrains.kotlin.fir.visitors.FirTransformer
import org.jetbrains.kotlin.text

internal class MyAnnotationTransformer(
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

    oldModules.transformChildren { element ->
      val oldModulesArray = element as? FirArrayLiteral
        ?: return@transformChildren element

      val oldArrayArgs = oldModulesArray.argumentList

      oldModulesArray.replaceArgumentList(
        buildArgumentList {
          source = oldArrayArgs.source?.fakeElement(KtFakeSourceElementKind.PluginGenerated)
          arguments += oldArrayArgs.arguments

          arguments += mergedModules.map { moduleSymbol ->
            buildGetClassClass(moduleSymbol)
          }
        },
      )

      oldModulesArray
    }

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

    return super.transformAnnotationCall(annotationCall, data)
  }
}

private class NamedArgumentTransformer : FirTransformer<Nothing?>() {
  override fun <E : FirElement> transformElement(element: E, data: Nothing?): E {

    return element
    error("transformElement -- $element")

    // // We want to handle only the most top-level "real" expressions
    // // We only recursively transform named, spread, lambda argument and vararg expressions.
    // if (element is FirWrappedArgumentExpression || element is FirVarargArgumentsExpression) {
    //   @Suppress("UNCHECKED_CAST")
    //   return element.transformChildren(this, null) as E
    // }
    //
    // // Once we encounter the first "real" expression, we delegate to the outer transformer.
    // val transformed = element.transformSingle(
    //   this@FirCallCompletionResultsWriterTransformer,
    //   expectedArgumentsTypeMapping,
    // )
    //
    // // Finally, the result can be wrapped in a SAM conversion if necessary.
    // if (transformed is FirExpression) {
    //   val key = (element as? FirAnonymousFunctionExpression)?.anonymousFunction ?: element
    //   expectedArgumentsTypeMapping?.samConversions?.get(key)?.let { samInfo ->
    //     @Suppress("UNCHECKED_CAST")
    //     return transformed.wrapInSamExpression(samInfo.samType) as E
    //   }
    // }
    //
    // return element
  }

  override fun transformNamedArgumentExpression(
    namedArgumentExpression: FirNamedArgumentExpression,
    data: Nothing?,
  ): FirStatement {

    val e = namedArgumentExpression.expression

    error("@@@@@@@@@@@@@@@@@@@@@@@ this expression -- $e")

    val array = e as? FirArrayLiteral
      ?: return super.transformNamedArgumentExpression(namedArgumentExpression, data)

    // array.transformChildren<FirArgumentList> { list ->
    //   error("######### existing list -- $list")
    //   list
    // }

    return namedArgumentExpression
  }
}
