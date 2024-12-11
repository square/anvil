package com.squareup.anvil.compiler.k2

import com.squareup.anvil.compiler.k2.internal.Names
import com.squareup.anvil.compiler.k2.internal.classId
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirClassLikeDeclaration
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.FirSupertypeGenerationExtension
import org.jetbrains.kotlin.fir.extensions.predicate.DeclarationPredicate
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.FirUserTypeRef
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtPsiFactory

public class AnvilFirAnnotationMergingExtension(session: FirSession) :
  FirSupertypeGenerationExtension(session) {

  private companion object {
    private val annotationClassId = Names.mergeComponentFir.classId()
    private val PREDICATE = DeclarationPredicate.create {
      annotated(annotationClassId.asSingleFqName())
    }
  }

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    register(PREDICATE)
  }

  override fun computeAdditionalSupertypes(
    classLikeDeclaration: FirClassLikeDeclaration,
    resolvedSupertypes: List<FirResolvedTypeRef>,
    typeResolver: TypeResolveService,
  ): List<FirResolvedTypeRef> {

    // clazz.transformAnnotations(MyAnnotationTransformer(mergedModules), Unit)

    // val symbol = clazz.symbol as? FirClassSymbol<*> ?: return emptyList()

    fun FirAnnotation.id(): FqName? = when (val t = this.annotationTypeRef) {
      is FirResolvedTypeRef -> t.coneType.classId?.asSingleFqName()
      is FirUserTypeRef ->
        typeResolver
          .resolveUserType(type = t)
          .coneType
          .classId
          ?.asSingleFqName()
      else -> error("~~~~~~~~~~~~~~ huh? $t")
    }

    fun PsiElement.ktPsiFactory(): KtPsiFactory {
      return KtPsiFactory.contextual(
        context = this@ktPsiFactory,
        markGenerated = false,
        eventSystemEnabled = false,
      )
    }

    val componentAnnotation = classLikeDeclaration.annotations
      .single { it.id() == Names.dagger.component }
      as FirAnnotationCall

    classLikeDeclaration.symbol
      // .resolvedAnnotationsWithArguments
      .annotations
      .forEach { firAnnotation ->

        firAnnotation as FirAnnotationCall

        val ref = firAnnotation.id()

        // val resolvedAnnotationCallType = typeResolver
        //   .resolveUserType(firAnnotation.annotationTypeRef.userType())

        val isComponent = ref == Names.dagger.component

        if (!isComponent) return@forEach

        // firAnnotation.argumentList
        //   .arguments
        //   .forEach { firExpression ->
        //     val moduleClassArray = firExpression.psi as? KtValueArgument
        //
        //     if (moduleClassArray == null) {
        //       println("moduleClassArray is null: $firExpression")
        //     }
        //
        //     // val arrayExpression = moduleClassArray.getArgumentExpression()
        //     //   as KtCollectionLiteralExpression
        //
        //     // val classes = arrayExpression.innerExpressions
        //     //   .map {
        //     //     val classLiteral = it as KtClassLiteralExpression
        //     //     classLiteral.fqNameOrNull()
        //     //   }
        //
        //     // arrayExpression
        //   }

        // (firAnnotation as FirAnnotationCall).argumentList
        //   .arguments
        //   .forEach { classListArg ->
        //
        //     // `modules = [SomeModule::class]`
        //     val modulesArg = classListArg.psi as? KtValueArgument
        //       ?: error("modules arg doesn't have a PSI element: ${classListArg.psi}")
        //
        //     // `[SomeModule::class]`
        //     val modulesListExpression =
        //       modulesArg.getArgumentExpression()!! as KtCollectionLiteralExpression
        //
        //     // `SomeModule::class`
        //     val moduleArgExpressions = modulesListExpression.innerExpressions
        //       // .map { it.requireFqName(org.jetbrains.kotlin.types.error.ErrorModuleDescriptor) }
        //       .map { it.text }
        //
        //     val newModules = listOf(Names.bBindingModule)
        //
        //     val allClassArgs = moduleArgExpressions
        //       .plus(newModules.map { "${it.asString()}::class" })
        //
        //     modulesArg
        //
        //     // val originalClassListPsi = modulesArg as? KtValueArgumentList
        //     //   ?: error("class list arg doesn't have a PSI element: ${classListArg.psi}")
        //
        //     val factory = modulesArg.ktPsiFactory()
        //
        //     fun createKClassValueArguments(typeFqNames: List<FqName>): KtValueArgumentList {
        //       return factory.createCallArguments(
        //         typeFqNames.joinToString(
        //           separator = ", ",
        //           prefix = "(",
        //           postfix = ")",
        //         ) { "${it.asString()}::class" },
        //       )
        //     }
        //
        //     // val originalClassArgText = originalClassListPsi.arguments.map { it.text }
        //
        //     // val newModules = listOf(Names.bBindingModule)
        //
        //     // val allClassArgs = originalClassArgText
        //     //   .plus(newModules.map { "${it.asString()}::class" })
        //
        //     val classArgList = allClassArgs.joinToString(separator = ", ")
        //
        //     val annotationEntry = factory.createAnnotationEntry(
        //       "@Component(modules = [$classArgList])",
        //     )
        //
        //     // psi.project.extensionArea.registerExtensionPoint(Extensions.getRootArea(),TreeCopyHandler.EP_NAME,)
        //     //
        //     val va = createKClassValueArguments(listOf(Names.bBindingModule))
        //
        //     va
        //
        //     // source.fakeElement()
        //     // KtFakeSourceElement()
        //
        //     // val newArg = factory.createArgument(
        //     //   expression = expression,
        //     //   name = null,
        //     //   isSpread = false,
        //     //   reformat = false,
        //     // )
        //
        //     // originalClassListPsi.parent.addAfter(va, originalClassListPsi)
        //
        //     // psi.astReplace(va)
        //
        //     classListArg
        //   }
      }

    classLikeDeclaration.transformAnnotations(
      MyAnnotationTransformer(
        session = session,
        typeResolver = typeResolver,
        mergedModules = {
          listOf(
            session.symbolProvider
              .getClassLikeSymbolByClassId(Names.bBindingModule.classId()) as FirRegularClassSymbol,
          )
        },
      ),
      Unit,
    )

    // classLikeDeclaration.replaceAnnotations(
    //   classLikeDeclaration.annotations
    //     .filterNot { it == componentAnnotation } +
    //     createMergedComponentAnnotation(session),
    // )

    // val evalBefore = FirExpressionEvaluator
    //   .evaluateAnnotationArguments(componentAnnotation, session)

    // error("@@@@@@@@@@@@@@ component annotation -- ${componentAnnotation.render()}  --  $evalBefore")

    // clazz.annotations.single().replaceAnnotationTypeRef(daggerComponentTypeRef)

    // clazz.annotations.forEach {
    //   println("Annotation: $it")
    //   it
    //   typeResolver.resolveUserType(it.rep)
    // }

    return listOf()
  }

  override fun needTransformSupertypes(declaration: FirClassLikeDeclaration): Boolean {
    return session.predicateBasedProvider.matches(PREDICATE, declaration)
  }
}
