package com.squareup.anvil.compiler.k2.constructor.inject

import com.squareup.anvil.compiler.k2.constructor.inject.FirInjectConstructorFactoryGenerationExtension.Key
import com.squareup.anvil.compiler.k2.utils.fir.createFirAnnotation
import com.squareup.anvil.compiler.k2.utils.fir.requireClassLikeSymbol
import com.squareup.anvil.compiler.k2.utils.names.ClassIds
import com.squareup.anvil.compiler.k2.utils.names.factoryJoined
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.builder.buildPackageDirective
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.builder.buildFile
import org.jetbrains.kotlin.fir.extensions.ExperimentalTopLevelDeclarationsGenerationApi
import org.jetbrains.kotlin.fir.extensions.FirExtension
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.plugin.createCompanionObject
import org.jetbrains.kotlin.fir.plugin.createConstructor
import org.jetbrains.kotlin.fir.plugin.createDefaultPrivateConstructor
import org.jetbrains.kotlin.fir.plugin.createMemberFunction
import org.jetbrains.kotlin.fir.plugin.createTopLevelClass
import org.jetbrains.kotlin.fir.resolve.providers.firProvider
import org.jetbrains.kotlin.fir.resolve.providers.impl.FirProviderImpl
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.types.constructClassLikeType
import org.jetbrains.kotlin.fir.types.constructType
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name

@OptIn(ExperimentalTopLevelDeclarationsGenerationApi::class)
internal class InjectConstructorGenerationModel(
  private val extension: FirExtension,
  private val session: FirSession,
  val matchedConstructorSymbol: FirConstructorSymbol,
) {
  val matchedClassId: ClassId by lazy { matchedConstructorSymbol.callableId.classId!! }

  val generatedClassId: ClassId by lazy { matchedClassId.factoryJoined }
  val generatedClassSymbol: FirClassSymbol<*> by lazy {
    val classSymbol = extension.createTopLevelClass(generatedClassId, Key) {
      superType(
        ClassIds.daggerFactory.requireClassLikeSymbol(session)
          .constructType(
            typeArguments = arrayOf(matchedClassId.constructClassLikeType()),
          ),
      )
    }
    buildFile {
      origin = FirDeclarationOrigin.Synthetic.Builtins
      moduleData = session.moduleData
      packageDirective = buildPackageDirective {
        this.packageFqName = matchedClassId.packageFqName
      }
      name = generatedClassId.asFqNameString()
      declarations += classSymbol
    }.also {
      (session.firProvider as FirProviderImpl).recordFile(file = it)
    }
    classSymbol.symbol
  }

  val generatedConstructor: FirConstructor by lazy {
    extension.createConstructor(
      owner = generatedClassSymbol,
      key = Key,
      isPrimary = true,
      generateDelegatedNoArgConstructorCall = true,
    ) {
      val providerSymbol = ClassIds.javaxProvider.requireClassLikeSymbol(session)
      for (param in matchedConstructorSymbol.valueParameterSymbols) {
        valueParameter(
          name = param.name,
          type = providerSymbol.constructType(
            typeArguments = arrayOf(param.resolvedReturnType),
          ),
        )
      }
    }
  }

  val generatedCallableIdToParameters: Map<CallableId, FirValueParameterSymbol> by lazy {
    generatedConstructor.symbol.valueParameterSymbols.associateBy {
      CallableId(classId = generatedClassId, callableName = it.name)
    }
  }

  val generatedCompanionClass: FirClassSymbol<*> by lazy {
    extension.createCompanionObject(generatedClassSymbol, Key) {
      this@createCompanionObject.visibility = Visibilities.Public
    }.symbol
  }

  val generatedCompanionConstructor: FirConstructor by lazy {
    extension.createDefaultPrivateConstructor(generatedCompanionClass, Key)
  }

  fun createFactoryGetFunction(): FirNamedFunctionSymbol {
    return extension.createMemberFunction(
      owner = generatedClassSymbol,
      key = Key,
      name = factoryGetName,
      returnType = matchedConstructorSymbol.resolvedReturnType,
    ) {
      visibility = Visibilities.Public
      modality = Modality.FINAL
    }.symbol
  }

  fun createCompanionCreateFunction(): FirNamedFunctionSymbol {
    val function = extension.createMemberFunction(
      owner = generatedCompanionClass,
      key = Key,
      name = createName,
      returnType = generatedClassSymbol.constructType(emptyArray()),
    ) {
      generatedConstructor.symbol.valueParameterSymbols.forEach { symbol ->
        this@createMemberFunction.valueParameter(
          name = symbol.name,
          type = symbol.resolvedReturnType,
        )
      }
    }
    function.replaceAnnotations(listOf(createFirAnnotation(ClassIds.kotlinJvmStatic)))
    return function.symbol
  }

  fun createCompanionNewInstanceFunction(): FirNamedFunctionSymbol {
    val function = extension.createMemberFunction(
      owner = generatedCompanionClass,
      key = Key,
      name = newInstance,
      returnType = matchedClassId.constructClassLikeType(),
    ) {
      matchedConstructorSymbol.valueParameterSymbols.forEach { symbol ->
        this@createMemberFunction.valueParameter(
          name = symbol.name,
          type = symbol.resolvedReturnType,
        )
      }
    }
    function.replaceAnnotations(listOf(createFirAnnotation(ClassIds.kotlinJvmStatic)))
    return function.symbol
  }

  companion object ConstructorInjectionNames {

    val factoryGetName = Name.identifier("get")
    val createName = Name.identifier("create")
    val newInstance = Name.identifier("newInstance")
  }
}
