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
import com.squareup.anvil.compiler.k2.internal.requireScopeArgument
import com.squareup.anvil.compiler.k2.internal.toFirAnnotation
import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.caches.FirCache
import org.jetbrains.kotlin.fir.caches.contains
import org.jetbrains.kotlin.fir.caches.firCachesFactory
import org.jetbrains.kotlin.fir.declarations.getKClassArgument
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotation
import org.jetbrains.kotlin.fir.expressions.impl.FirAnnotationArgumentMappingImpl
import org.jetbrains.kotlin.fir.extensions.ExperimentalTopLevelDeclarationsGenerationApi
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.FirExtension
import org.jetbrains.kotlin.fir.extensions.FirExtensionSessionComponent
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.plugin.createMemberFunction
import org.jetbrains.kotlin.fir.plugin.createTopLevelClass
import org.jetbrains.kotlin.fir.references.FirNamedReference
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

/**
 * Responsible for tracking the classes annotated with @ContributesBinding and creating + caching
 * their generated Dagger module metadata.
 */
public class ContributesBindingSessionComponent(session: FirSession) : FirExtensionSessionComponent(
  session,
) {
  /**
   * A map to help us track the original annotated classes' bindings, and their
   * generated module IDs.
   * E.g. Key: "Foo_BindingModule", Value: ClassSymbol<Foo>
   */
  public val generatedIdsToMatchedSymbols: Map<ClassId, FirClassSymbol<*>> by lazy {
    session.predicateBasedProvider.getSymbolsByPredicate(hasContributesBindingAnnotation)
      .filterIsInstance<FirClassSymbol<*>>()
      .associateBy {
        it.classId.asClassName()
          .joinSimpleNames(suffix = "_BindingModule")
          .asClassId()
      }
  }

  public val bindingModuleCache: FirCache<ClassId, ContributesBindingFirExtension.BindModulesData, FirSession> =
    session.firCachesFactory
      .createCache<ClassId, ContributesBindingFirExtension.BindModulesData, FirSession> { key: ClassId, context ->
        ContributesBindingFirExtension.BindModulesData(
          key,
          generatedIdsToMatchedSymbols[key] as FirClassSymbol<*>,
          this@ContributesBindingSessionComponent,
          session,
        )
      }

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    register(hasContributesBindingAnnotation)
  }
}

public val FirSession.contributesBindingSessionComponent: ContributesBindingSessionComponent by FirSession.sessionComponentAccessor()

@OptIn(ExperimentalTopLevelDeclarationsGenerationApi::class)
public class ContributesBindingFirExtension(
  session: FirSession,
) : FirDeclarationGenerationExtension(session) {
  public companion object Key : GeneratedDeclarationKey()

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    register(hasContributesBindingAnnotation)
  }

  override fun getTopLevelClassIds(): Set<ClassId> {
    return session.contributesBindingSessionComponent.generatedIdsToMatchedSymbols.keys
  }

  override fun generateTopLevelClassLikeDeclaration(classId: ClassId): FirClassLikeSymbol<*> {
    val matchedSymbol =
      session.contributesBindingSessionComponent.bindingModuleCache.getValue(classId, session)

    return matchedSymbol.generatedClassSymbol
  }

  override fun getCallableNamesForClass(
    classSymbol: FirClassSymbol<*>,
    context: MemberGenerationContext
  ): Set<Name> {
    if (!session.contributesBindingSessionComponent.bindingModuleCache.contains(classSymbol.classId)) {
      return emptySet()
    }
    val data = session.contributesBindingSessionComponent.bindingModuleCache.getValue(
      classSymbol.classId,
      session,
    )

    return setOf(data.callableName)
  }

  override fun generateFunctions(
    callableId: CallableId,
    context: MemberGenerationContext?
  ): List<FirNamedFunctionSymbol> {
    val data = session.contributesBindingSessionComponent.bindingModuleCache.getValue(
      context!!.owner.classId,
      session,
    )

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

  public class BindModulesData(
    public val generatedClassId: ClassId,
    public val matchedClassSymbol: FirClassSymbol<*>,
    public val firExtension: FirExtension,
    public val session: FirSession,
  ) {
    public val generatedClassSymbol: FirClassLikeSymbol<*> by lazy {
      firExtension.createTopLevelClass(
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
        contributesBindingAnnotation.argumentMapping.mapping
          .filter { (key, _) ->
            key.asString() == "scope"
          },
      )
    }

    public val contributesBindingAnnotation: FirAnnotation by lazy {
      matchedClassSymbol.annotations.single {
        it.fqName(session)?.equals(contributesBinding) ?: false
      }
    }
    public val scope: ConeKotlinType by lazy {
      // TODO add logic for repeated annotations.
      // TODO add logic to interpret default value when only a single parent exists.
      contributesBindingAnnotation.getKClassArgument(Name.identifier("scope"), session)!!
    }

    public val boundType: ConeKotlinType by lazy {
      // TODO add logic for repeated annotations.
      // TODO add logic to interpret default value when only a single parent exists.
      contributesBindingAnnotation.getKClassArgument(Name.identifier("boundType"), session)!!
    }

    public val callableName: Name by lazy {
      "bind${boundType.classId!!.asClassName().simpleName}"
        .let(Name::identifier)
    }
  }
}
