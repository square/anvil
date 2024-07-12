package com.squareup.anvil.compiler.k2

import com.squareup.anvil.compiler.k2.internal.Names
import com.squareup.anvil.compiler.k2.internal.classId
import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.fakeElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirClassLikeDeclaration
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotationArgumentMapping
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotationCall
import org.jetbrains.kotlin.fir.expressions.builder.buildArgumentList
import org.jetbrains.kotlin.fir.expressions.builder.buildArrayLiteral
import org.jetbrains.kotlin.fir.expressions.builder.buildClassReferenceExpression
import org.jetbrains.kotlin.fir.extensions.buildUserTypeFromQualifierParts
import org.jetbrains.kotlin.fir.resolve.defaultType
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.toFirResolvedTypeRef
import org.jetbrains.kotlin.name.Name

internal fun FirClassLikeDeclaration.addMergedComponentAnnotation(
  session: FirSession,
  sourceAnnotation: FirAnnotationCall,
) {
  replaceAnnotations(
    annotations + listOf(
      createMergedComponentAnnotation(
        session = session,
        sourceAnnotation = sourceAnnotation,
      ),
    ),
  )
}

internal fun createMergedComponentAnnotation(
  session: FirSession,
  sourceAnnotation: FirAnnotationCall,
): FirAnnotation = buildAnnotationCall {

  source = sourceAnnotation.source?.fakeElement(KtFakeSourceElementKind.PluginGenerated)

  val componentAnnotationClassSymbol = session.symbolProvider
    .getClassLikeSymbolByClassId(Names.dagger.component.classId())
    as FirRegularClassSymbol

  annotationTypeRef = componentAnnotationClassSymbol.defaultType().toFirResolvedTypeRef()

  // TODO - Hard-code `EmptyModule` for now, but this would need to happen for all merged modules.
  val newModules = listOf(
    session.symbolProvider.getClassLikeSymbolByClassId(Names.emptyModule.classId())
      as FirRegularClassSymbol,
  )

  // argumentList = buildArgumentList {
  //   // source = oldArrayArgs.source?.fakeElement(KtFakeSourceElementKind.PluginGenerated)
  //   // source = oldArrayArgs.source?.fakeElement(KtFakeSourceElementKind.PluginGenerated)
  //   arguments += sourceAnnotation.argumentList.arguments
  //
  //   arguments += mergedModules.map { moduleSymbol ->
  //     buildGetClassClass(moduleSymbol)
  //   }
  // }

  val moduleArgs = buildAnnotationArgumentMapping {
    mapping[Name.identifier("modules")] = buildArrayLiteral array@{
      this@array.argumentList = buildArgumentList argList@{
        this@argList.arguments += newModules.map { module ->
          buildClassReferenceExpression {
            classTypeRef = buildUserTypeFromQualifierParts(false) {
              module.classId.asSingleFqName().pathSegments().forEach(::part)
            }
            coneTypeOrNull = null
          }
        }
      }
    }
  }

  argumentMapping = moduleArgs
}
