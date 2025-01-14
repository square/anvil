package com.squareup.anvil.compiler.k2

import com.squareup.anvil.compiler.k2.internal.AnvilPredicates
import com.squareup.anvil.compiler.k2.internal.Names
import com.squareup.anvil.compiler.k2.internal.classId
import com.squareup.anvil.compiler.k2.internal.getGetKClassArgument
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirClassLikeDeclaration
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirGetClassCall
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.UnresolvedExpressionTypeAccess
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.FirSupertypeGenerationExtension
import org.jetbrains.kotlin.fir.extensions.buildUserTypeFromQualifierParts
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.plugin.createConeType
import org.jetbrains.kotlin.fir.references.impl.FirSimpleNamedReference
import org.jetbrains.kotlin.fir.resolve.fqName
import org.jetbrains.kotlin.fir.resolve.providers.FirSymbolNamesProvider
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.name.Name

public class AnvilFirInterfaceMergingExtension(session: FirSession) :
  FirSupertypeGenerationExtension(session) {

  private companion object {
    private val PREDICATE = AnvilPredicates.hasMergeComponentFirAnnotation
  }

  private val predicateBasedProvider = session.predicateBasedProvider
  private val sessionForReal = session
  private val scopeToSupertypes by lazy {
    val all = predicateBasedProvider.getSymbolsByPredicate(AnvilPredicates.hasContributesToAnnotation)
    all.filterIsInstance<FirClassLikeSymbol<*>>()
  }

  @OptIn(UnresolvedExpressionTypeAccess::class)
  private fun FirAnnotation.scopeArgument(): FirGetClassCall {

    return getGetKClassArgument(Name.identifier("scope")) ?: error("no")
  }

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    register(PREDICATE)
    register(AnvilPredicates.hasContributesToAnnotation)
    register(AnvilPredicates.hasModuleAnnotation)
  }

  private val symbolNamesProvider: FirSymbolNamesProvider by lazy {
    session.symbolProvider.symbolNamesProvider
  }

  override fun computeAdditionalSupertypes(
    classLikeDeclaration: FirClassLikeDeclaration,
    resolvedSupertypes: List<FirResolvedTypeRef>,
    typeResolver: TypeResolveService,
  ): List<ConeKotlinType> {

//    val scopeFqName = classLikeDeclaration.annotations
//      // TODO There could be multiple MergeComponent annotations.
//      .single { it.fqName(session) == Names.anvil.mergeComponent }
//      .scopeArgument()
//      .let { getClass ->
//
//        resolveConeTypeFromArgument(getClass, typeResolver).classId?.asSingleFqName() ?: error("no")
//      }

    val packageNames = listOf(Names.foo.packageFqName)

    val lotsOfThings = packageNames.flatMap { packageFqName ->

      symbolNamesProvider.getTopLevelClassifierNamesInPackage(packageFqName).orEmpty()
        .map { packageFqName.child(it) }
    }
      .map { className ->
        session.symbolProvider.getClassLikeSymbolByClassId(className.classId())
      }

    val contributedClasses = lotsOfThings.filterNotNull()
      .filter { clazz ->

        if (session.predicateBasedProvider.matches(AnvilPredicates.hasModuleAnnotation, clazz)) {
          return@filter false
        }

        clazz.annotations
          .filterIsInstance<FirAnnotationCall>()
          .any { annotation ->

            println(
              "clazz: ${clazz.classId}  --  phase: ${annotation.annotationResolvePhase}  --  annotation -- ${
                annotation.fqName(
                  session,
                )
              }",
            )
            annotation.fqName(session) == Names.anvil.contributesTo
          }
      }
      .map { it.classId }

    return contributedClasses.map { supertypeUserType ->

//      val alreadyHasComponentBase = resolvedSupertypes.any {
//        it.coneType.classId?.asFqNameString() == Names.foo.componentBase.asString()
//      }
//      if (alreadyHasComponentBase) return@mapNotNull null

      val superResolved = supertypeUserType.createConeType(session)

//      check(!resolvedSupertypes.contains(superResolved)) {
//        "Supertype $supertypeUserType is already present in $resolvedSupertypes"
//      }

      superResolved
    }
  }

  override fun needTransformSupertypes(declaration: FirClassLikeDeclaration): Boolean {
    return session.predicateBasedProvider.matches(PREDICATE, declaration)
  }

  // https://github.com/JetBrains/kotlin/blob/master/plugins/kotlinx-serialization/kotlinx-serialization.k2/src/org/jetbrains/kotlinx/serialization/compiler/fir/SerializationFirSupertypesExtension.kt
  private fun resolveConeTypeFromArgument(
    getClassCall: FirGetClassCall,
    typeResolver: TypeResolveService,
  ): ConeKotlinType {
    val typeToResolve = buildUserTypeFromQualifierParts(isMarkedNullable = false) {
      fun visitQualifiers(expression: FirExpression) {
        if (expression !is FirPropertyAccessExpression) return
        expression.explicitReceiver?.let { visitQualifiers(it) }
        expression.qualifierName?.let { part(it) }
      }
      visitQualifiers(getClassCall.argument)
    }
    return typeResolver.resolveUserType(typeToResolve).coneType
  }

  private val FirPropertyAccessExpression.qualifierName: Name?
    get() = (calleeReference as? FirSimpleNamedReference)?.name
}
