package com.squareup.anvil.compiler.k2

import com.squareup.anvil.compiler.contributesToFqName
import com.squareup.anvil.compiler.daggerModuleFqName
import com.squareup.anvil.compiler.internal.asClassName
import com.squareup.anvil.compiler.internal.joinSimpleNames
import com.squareup.anvil.compiler.internal.reference.asClassId
import com.squareup.anvil.compiler.k2.internal.AnvilPredicates.hasContributesBindingAnnotation
import com.squareup.anvil.compiler.k2.internal.Names
import com.squareup.anvil.compiler.k2.internal.Names.anvil.contributesBinding
import com.squareup.anvil.compiler.k2.internal.classId
import com.squareup.anvil.compiler.k2.internal.toFirAnnotation
import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.getKClassArgument
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotation
import org.jetbrains.kotlin.fir.expressions.impl.FirAnnotationArgumentMappingImpl
import org.jetbrains.kotlin.fir.extensions.ExperimentalTopLevelDeclarationsGenerationApi
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.plugin.createMemberFunction
import org.jetbrains.kotlin.fir.plugin.createTopLevelClass
import org.jetbrains.kotlin.fir.resolve.fqName
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.constructClassLikeType
import org.jetbrains.kotlin.fir.types.constructType
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name

@OptIn(ExperimentalTopLevelDeclarationsGenerationApi::class)
public class ContributesBindingFirExtension(
  session: FirSession,
) : FirDeclarationGenerationExtension(session) {
  public companion object Key : GeneratedDeclarationKey()

  private val modulesToGenerate: Map<ClassId, BindModulesData> by lazy {
    session.predicateBasedProvider.getSymbolsByPredicate(hasContributesBindingAnnotation)
      .filterIsInstance<FirClassSymbol<*>>()
      .associate { classSymbol ->
        val data = BindModulesData(classSymbol)
        data.generatedClassId to data
      }
  }

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    register(hasContributesBindingAnnotation)
  }

  override fun getTopLevelClassIds(): Set<ClassId> {
    return modulesToGenerate.keys
  }

  override fun generateTopLevelClassLikeDeclaration(classId: ClassId): FirClassLikeSymbol<*> {
    return modulesToGenerate[classId]!!.generatedClassSymbol
  }

  override fun getCallableNamesForClass(
    classSymbol: FirClassSymbol<*>,
    context: MemberGenerationContext
  ): Set<Name> {
    val data = modulesToGenerate[classSymbol.classId] ?: return emptySet()
    return setOf(data.callableName)
  }

  override fun generateFunctions(
    callableId: CallableId,
    context: MemberGenerationContext?
  ): List<FirNamedFunctionSymbol> {
    val data = modulesToGenerate[context!!.owner.classId]!!
    return listOf(
      createMemberFunction(
        owner = context.owner,
        key = Key,
        name = callableId.callableName,
        returnType = data.boundType,
      ) {
        modality = Modality.ABSTRACT
        valueParameter(
          name = Name.identifier("concreteType"),
          type = data.matchedClassSymbol.constructType(),
        )
      }.apply {
        replaceAnnotations(listOf(Names.dagger.binds.toFirAnnotation()))
      }.symbol,
    )
  }

  internal inner class BindModulesData(
    val matchedClassSymbol: FirClassSymbol<*>,
  ) {
    val generatedClassId: ClassId by lazy {
      matchedClassSymbol.classId.asClassName()
        .joinSimpleNames(suffix = "_BindingModule")
        .asClassId()
    }
    val generatedClassSymbol: FirClassLikeSymbol<*> by lazy {
      createTopLevelClass(
        classId = generatedClassId,
        key = Key,
        classKind = ClassKind.INTERFACE,
      ) {

      }.apply {
        replaceAnnotations(
          listOf(
            buildContributesToAnnotation(),
            daggerModuleFqName.toFirAnnotation(),
          ),
        )
      }.symbol
    }

    private fun buildContributesToAnnotation(): FirAnnotation = buildAnnotation {
      annotationTypeRef = buildResolvedTypeRef {
        coneType = contributesToFqName.classId().constructClassLikeType()
      }
      argumentMapping = FirAnnotationArgumentMappingImpl(
              null,
              contributesBindingAnnotation.argumentMapping.mapping.filter { (key, _) ->
                  key.asString() == "scope"
              },
      )
    }

    val contributesBindingAnnotation: FirAnnotation by lazy {
      matchedClassSymbol.annotations.single {
        it.fqName(session)?.equals(contributesBinding) ?: false
      }
    }
    val scope: ConeKotlinType by lazy {
      // TODO add logic for repeated annotations.
      // TODO add logic to interpret default value when only a single parent exists.
      contributesBindingAnnotation.getKClassArgument(Name.identifier("scope"), session)!!
    }

    val boundType: ConeKotlinType by lazy {
      // TODO add logic for repeated annotations.
      // TODO add logic to interpret default value when only a single parent exists.
      contributesBindingAnnotation.getKClassArgument(Name.identifier("boundType"), session)!!
    }

    val callableName by lazy {
      "bind${boundType.classId!!.asClassName().simpleName}"
        .let(Name::identifier)
    }
  }
}
