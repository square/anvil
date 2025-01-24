package com.squareup.anvil.compiler.k2

import org.jetbrains.kotlin.fir.extensions.ExperimentalTopLevelDeclarationsGenerationApi
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.extensions.NestedClassGenerationContext
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

public open class FirDeclarationGenerationExtensionLogger(
  private val firDeclarationGenerationExtension: FirDeclarationGenerationExtension,
) : FirDeclarationGenerationExtension(firDeclarationGenerationExtension.session) {
  override fun generateConstructors(
    context: MemberGenerationContext
  ): List<FirConstructorSymbol> {
    println("$LOG_PREFIX generateConstructors for: ${context.owner.classId}")
    val constructors = firDeclarationGenerationExtension.generateConstructors(context)
    println("$LOG_PREFIX generateConstructors\n  result: $constructors")
    return constructors
  }

  override fun generateFunctions(
    callableId: CallableId,
    context: MemberGenerationContext?
  ): List<FirNamedFunctionSymbol> {
    println("$LOG_PREFIX generateFunctions\n  callableId: $callableId\n  context: ${context?.owner?.classId}")
    val functions = firDeclarationGenerationExtension.generateFunctions(callableId, context)
    println("$LOG_PREFIX generateFunctions\n  result: $functions")
    return functions
  }

  override fun generateNestedClassLikeDeclaration(
    owner: FirClassSymbol<*>,
    name: Name,
    context: NestedClassGenerationContext
  ): FirClassLikeSymbol<*>? {
    println("$LOG_PREFIX generateNestedClassLike\n  owner: $owner\n  $name\n  context: ${context.owner.classId}")
    val nestedClassLikeDeclaration = firDeclarationGenerationExtension.generateNestedClassLikeDeclaration(owner, name, context)
    println("$LOG_PREFIX generateNestedClassLike\n  result:  $nestedClassLikeDeclaration")
    return nestedClassLikeDeclaration
  }

  override fun generateProperties(
    callableId: CallableId,
    context: MemberGenerationContext?
  ): List<FirPropertySymbol> {
    println("$LOG_PREFIX generateProperties\n  callableId: $callableId\n  context: ${context?.owner?.classId}")
    val properties = firDeclarationGenerationExtension.generateProperties(callableId, context)
    println("$LOG_PREFIX generateProperties\n  result: $properties")
    return properties
  }

  @ExperimentalTopLevelDeclarationsGenerationApi
  override fun generateTopLevelClassLikeDeclaration(
    classId: ClassId
  ): FirClassLikeSymbol<*>? {
    println("$LOG_PREFIX generateTopLevelClassLike\n  classId: $classId")
    val topLevelClassLikeDeclaration = firDeclarationGenerationExtension.generateTopLevelClassLikeDeclaration(classId)
    println("$LOG_PREFIX generateTopLevelClassLike\n  result: $topLevelClassLikeDeclaration")
    return topLevelClassLikeDeclaration
  }

  override fun getCallableNamesForClass(
    classSymbol: FirClassSymbol<*>,
    context: MemberGenerationContext
  ): Set<Name> {
    println("$LOG_PREFIX getCallableNamesForClass\n  classSymbol: $classSymbol\n  context: ${context.owner.classId}")
    val getCallableNamesForClass = firDeclarationGenerationExtension.getCallableNamesForClass(classSymbol, context)
    println("$LOG_PREFIX getCallableNamesForClass\n  result: $getCallableNamesForClass")
    return getCallableNamesForClass
  }

  override fun getNestedClassifiersNames(
    classSymbol: FirClassSymbol<*>,
    context: NestedClassGenerationContext
  ): Set<Name> {
    println("$LOG_PREFIX getNestedClassifiersNames\n  classSymbol: $classSymbol\n  context: ${context.owner.classId}")
    val getNestedClassifierNames = firDeclarationGenerationExtension.getNestedClassifiersNames(classSymbol, context)
    println("$LOG_PREFIX getNestedClassifierNames\n  result: $getNestedClassifierNames")
    return getNestedClassifierNames
  }

  @ExperimentalTopLevelDeclarationsGenerationApi
  override fun getTopLevelCallableIds(): Set<CallableId> {
    println("$LOG_PREFIX getTopLevelCallableIds")
    val topLevelCallableIds = firDeclarationGenerationExtension.getTopLevelCallableIds()
    println("$LOG_PREFIX getTopLevelCallableIds: \n  result: $topLevelCallableIds")
    return topLevelCallableIds
  }

  @ExperimentalTopLevelDeclarationsGenerationApi
  override fun getTopLevelClassIds(): Set<ClassId> {
    println("$LOG_PREFIX getTopLevelClassIds")
    val topLevelClassIds = firDeclarationGenerationExtension.getTopLevelClassIds()
    println("$LOG_PREFIX getTopLevelClassIds: \n  result: $topLevelClassIds")
    return topLevelClassIds
  }

  override fun hasPackage(packageFqName: FqName): Boolean {
    return firDeclarationGenerationExtension.hasPackage(packageFqName)
  }

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    firDeclarationGenerationExtension.run { registerPredicates() }
  }

  public companion object {
    private const val LOG_PREFIX = "FirLogger: "
  }
}
