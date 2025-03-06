package com.squareup.anvil.compiler.k2.fir.contributions

import com.squareup.anvil.compiler.k2.utils.fir.createFirAnnotation
import com.squareup.anvil.compiler.k2.utils.names.ClassIds
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.getKClassArgument
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.expressions.FirNamedArgumentExpression
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotation
import org.jetbrains.kotlin.fir.expressions.impl.FirAnnotationArgumentMappingImpl
import org.jetbrains.kotlin.fir.extensions.ExperimentalTopLevelDeclarationsGenerationApi
import org.jetbrains.kotlin.fir.extensions.FirExtension
import org.jetbrains.kotlin.fir.plugin.createTopLevelClass
import org.jetbrains.kotlin.fir.resolve.fqName
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.constructClassLikeType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name

@OptIn(ExperimentalTopLevelDeclarationsGenerationApi::class)
public class BindingModuleData(
  public val generatedClassId: ClassId,
  public val matchedClassSymbol: FirClassSymbol<*>,
  firExtension: FirExtension,
  session: FirSession,
) {
  public val generatedClassSymbol: FirClassLikeSymbol<*> by lazy {
    firExtension.createTopLevelClass(
      classId = generatedClassId,
      key = GeneratedBindingDeclarationKey,
      classKind = ClassKind.INTERFACE,
    ).apply {
      replaceAnnotations(
        listOf(
          buildContributesToAnnotation(),
          createFirAnnotation(ClassIds.daggerModule),
        ),
      )
    }.symbol
  }

  public val contributesBindingAnnotation: FirAnnotation by lazy {
    matchedClassSymbol.annotations.single {
      it.fqName(session) == ClassIds.anvilContributesBinding.asSingleFqName()
    }
  }

  public val boundType: ConeKotlinType by lazy {
    contributesBindingAnnotation.getKClassArgument(Name.identifier("boundType"), session)!!
  }

  public val callableName: Name by lazy {
    "bind${boundType.classId!!.shortClassName.asString()}"
      .let(Name::identifier)
  }

  private fun buildContributesToAnnotation(): FirAnnotation = buildAnnotation {
    annotationTypeRef = buildResolvedTypeRef {
      coneType = ClassIds.anvilContributesTo.constructClassLikeType()
    }
    // Argument mapping may be empty if merging is also happening
    val newArgMapping = if (contributesBindingAnnotation.argumentMapping.mapping.isEmpty()) {
      val scopeArg = (contributesBindingAnnotation as FirAnnotationCall).argumentList
        .arguments
        .single { (it as FirNamedArgumentExpression).name.toString() == "scope" }

      mapOf(Name.identifier("scope") to scopeArg)
    } else {
      contributesBindingAnnotation.argumentMapping.mapping
        .filter { (key, _) ->
          key.asString() == "scope"
        }
    }

    argumentMapping = FirAnnotationArgumentMappingImpl(null, newArgMapping)
  }
}
