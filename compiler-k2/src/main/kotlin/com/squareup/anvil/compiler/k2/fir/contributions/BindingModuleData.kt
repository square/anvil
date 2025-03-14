package com.squareup.anvil.compiler.k2.fir.contributions

import com.squareup.anvil.compiler.k2.utils.fir.createFirAnnotation
import com.squareup.anvil.compiler.k2.utils.fir.requireAnnotationCall
import com.squareup.anvil.compiler.k2.utils.fir.wrapInSyntheticFile
import com.squareup.anvil.compiler.k2.utils.names.ClassIds
import com.squareup.anvil.compiler.k2.utils.names.Names
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.getKClassArgument
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.extensions.ExperimentalTopLevelDeclarationsGenerationApi
import org.jetbrains.kotlin.fir.extensions.FirExtension
import org.jetbrains.kotlin.fir.plugin.createTopLevelClass
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name

@OptIn(ExperimentalTopLevelDeclarationsGenerationApi::class)
public class BindingModuleData(
  public val generatedClassId: ClassId,
  public val matchedClassSymbol: FirRegularClassSymbol,
  firExtension: FirExtension,
  private val session: FirSession,
) {

  public val generatedClass: FirRegularClass by lazy {
    firExtension.createTopLevelClass(
      classId = generatedClassId,
      key = GeneratedBindingDeclarationKey,
      classKind = ClassKind.INTERFACE,
    ).apply {
      replaceAnnotations(
        listOf(createFirAnnotation(ClassIds.daggerModule)),
      )
    }
  }

  public val generatedClassSymbol: FirClassLikeSymbol<*> by lazy {
    generatedClass
      .wrapInSyntheticFile(session)
      .symbol
  }

  public val contributesBindingAnnotation: FirAnnotationCall by lazy {
    matchedClassSymbol.requireAnnotationCall(
      ClassIds.anvilContributesBinding,
      session,
      resolveArguments = true,
    )
  }

  public val boundType: ConeKotlinType by lazy {
    contributesBindingAnnotation.getKClassArgument(Names.boundType, session)
      ?: matchedClassSymbol.resolvedSuperTypes.single()
  }

  public val callableName: Name by lazy {
    "bind${boundType.classId!!.shortClassName.asString()}"
      .let(Name::identifier)
  }
}
