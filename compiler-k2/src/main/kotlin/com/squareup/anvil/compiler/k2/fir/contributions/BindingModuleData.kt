package com.squareup.anvil.compiler.k2.fir.contributions

import com.squareup.anvil.compiler.k2.utils.names.ClassIds
import com.squareup.anvil.compiler.k2.fir.internal.requireAnnotation
import com.squareup.anvil.compiler.k2.fir.internal.requireClassId
import com.squareup.anvil.compiler.k2.fir.internal.requireScopeArgument
import com.squareup.anvil.compiler.k2.util.toFirAnnotation
import com.squareup.anvil.compiler.k2.utils.fir.requireAnnotationCall
import com.squareup.anvil.compiler.k2.utils.fir.requireClassId
import com.squareup.anvil.compiler.k2.utils.fir.requireClassLikeSymbol
import com.squareup.anvil.compiler.k2.utils.fir.requireScopeArgument
import com.squareup.anvil.compiler.k2.utils.fir.toGetClassCall
import com.squareup.anvil.compiler.k2.utils.fir.createFirAnnotation
import com.squareup.anvil.compiler.k2.utils.names.Names
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.builder.buildPackageDirective
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.builder.buildFile
import org.jetbrains.kotlin.fir.declarations.getKClassArgument
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotation
import org.jetbrains.kotlin.fir.expressions.impl.FirAnnotationArgumentMappingImpl
import org.jetbrains.kotlin.fir.extensions.ExperimentalTopLevelDeclarationsGenerationApi
import org.jetbrains.kotlin.fir.extensions.FirExtension
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.plugin.createTopLevelClass
import org.jetbrains.kotlin.fir.resolve.providers.firProvider
import org.jetbrains.kotlin.fir.resolve.providers.impl.FirProviderImpl
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.constructClassLikeType
import org.jetbrains.kotlin.fir.visitors.FirDefaultTransformer
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
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

    val bindingFile = buildFile {
      origin = FirDeclarationOrigin.Synthetic.Builtins
      moduleData = session.moduleData
      packageDirective = buildPackageDirective {
        this.packageFqName = generatedClassId.packageFqName
      }
      name = generatedClassId.asFqNameString()
      declarations += generatedClass
    }

    (session.firProvider as FirProviderImpl).recordFile(file = bindingFile)

    val matchedFile = session.firProvider.getFirFilesByPackage(generatedClassId.packageFqName)
      .single { it.declarations.any { it is FirRegularClass && it.symbol == matchedClassSymbol } }

    matchedFile.transformDeclarations(
      object : FirDefaultTransformer<Nothing?>() {
        override fun <E : FirElement> transformElement(element: E, data: Nothing?): E {
          println("########## element: $element")
          return element
        }
      },
      null,
    )

    generatedClass.symbol
  }

  public val contributesBindingAnnotation: FirAnnotationCall by lazy {
    matchedClassSymbol.requireAnnotationCall(ClassIds.anvilContributesBinding, session)
  }

  public val boundType: ConeKotlinType by lazy {
    contributesBindingAnnotation.getKClassArgument(Names.boundType, session)
      ?: matchedClassSymbol.resolvedSuperTypes.single()
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
      val scopeArg = contributesBindingAnnotation.requireScopeArgument(session)
        .requireClassId()
        .requireClassLikeSymbol(session)
        .toGetClassCall()

      mapOf(Names.scope to scopeArg)
    } else {
      contributesBindingAnnotation.argumentMapping.mapping
        .filter { (key, _) ->
          key.asString() == "scope"
        }
    }

    argumentMapping = FirAnnotationArgumentMappingImpl(null, newArgMapping)
  }
}

public fun FirSession.createSyntheticFile(
  origin: FirDeclarationOrigin,
  packageName: FqName,
  simpleName: String,
  declarations: List<FirDeclaration>,
): FirFile = buildFile {
  this.origin = origin
  this@buildFile.moduleData = this@createSyntheticFile.moduleData
  packageDirective = buildPackageDirective {
    this.packageFqName = packageName
  }
  this.name = simpleName
  this.declarations.addAll(declarations)
}.also {
  (firProvider as FirProviderImpl).recordFile(it)
}
