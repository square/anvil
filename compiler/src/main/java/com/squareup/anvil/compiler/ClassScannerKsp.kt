package com.squareup.anvil.compiler

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.Origin
import com.google.devtools.ksp.symbol.Visibility
import com.squareup.anvil.compiler.ClassScannerKsp.GeneratedProperty.ReferenceProperty
import com.squareup.anvil.compiler.ClassScannerKsp.GeneratedProperty.ScopeProperty
import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.codegen.ksp.KSCallable
import com.squareup.anvil.compiler.codegen.ksp.KspAnvilException
import com.squareup.anvil.compiler.codegen.ksp.KspTracer
import com.squareup.anvil.compiler.codegen.ksp.contextualToClassName
import com.squareup.anvil.compiler.codegen.ksp.fqName
import com.squareup.anvil.compiler.codegen.ksp.getAllCallables
import com.squareup.anvil.compiler.codegen.ksp.isAbstract
import com.squareup.anvil.compiler.codegen.ksp.isInterface
import com.squareup.anvil.compiler.codegen.ksp.resolvableAnnotations
import com.squareup.anvil.compiler.codegen.ksp.resolveKSClassDeclaration
import com.squareup.anvil.compiler.codegen.ksp.scope
import com.squareup.anvil.compiler.codegen.ksp.trace
import com.squareup.anvil.compiler.codegen.ksp.type
import org.jetbrains.kotlin.name.FqName

internal class ClassScannerKsp(
  tracer: KspTracer,
) : KspTracer by tracer {
  private val _hintCache =
    RecordingCache<FqName, Map<KSType, Set<ContributedType>>>("Generated Property")

  private val parentComponentCache = RecordingCache<FqName, FqName?>("ParentComponent")

  private val overridableParentComponentCallableCache =
    RecordingCache<FqName, List<KSCallable.CacheEntry>>("Overridable ParentComponent Callable")

  /**
   * Externally-contributed contributions, which are important to track so that we don't try to
   * add originating files for them when generating code.
   */
  private val externalContributions = mutableSetOf<FqName>()

  fun isExternallyContributed(declaration: KSClassDeclaration): Boolean {
    return declaration.fqName in externalContributions
  }

  private fun KSTypeReference.resolveKClassType(): KSType {
    return resolve()
      .arguments.single().type!!.resolve()
  }

  private var hintCacheWarmer: (() -> Unit)? = null
  private val hintCache: RecordingCache<FqName, Map<KSType, Set<ContributedType>>>
    get() {
      hintCacheWarmer?.invoke()
      hintCacheWarmer = null
      return _hintCache
    }
  private var roundStarted = false

  fun startRound(resolver: Resolver) {
    if (roundStarted) return
    roundStarted = true
    hintCacheWarmer = {
      _hintCache += trace("Warming hint cache") {
        generateHintCache(resolver)
      }
    }
  }

  @OptIn(KspExperimental::class)
  private fun generateHintCache(
    resolver: Resolver,
  ): MutableMap<FqName, MutableMap<KSType, MutableSet<ContributedType>>> {
    val contributedTypes = resolver.getDeclarationsFromPackage(HINT_PACKAGE)
      .filterIsInstance<KSPropertyDeclaration>()
      .mapNotNull(GeneratedProperty::from)
      .groupBy(GeneratedProperty::baseName)
      .map { (name, properties) ->
        val refProp = properties.filterIsInstance<ReferenceProperty>()
          // In some rare cases we can see a generated property for the same identifier.
          // Filter them just in case, see https://github.com/square/anvil/issues/460 and
          // https://github.com/square/anvil/issues/565
          .distinctBy { it.baseName }
          .singleOrEmpty()
          ?: throw AnvilCompilationException(
            message = "Couldn't find the reference for a generated hint: ${properties[0].baseName}.",
          )

        val scopes = properties.filterIsInstance<ScopeProperty>()
          .ifEmpty {
            throw AnvilCompilationException(
              message = "Couldn't find any scope for a generated hint: ${properties[0].baseName}.",
            )
          }
          .mapTo(mutableSetOf()) {
            it.declaration.type.resolveKClassType()
          }

        ContributedType(
          baseName = name,
          reference = refProp.declaration.type
            .resolveKClassType()
            .resolveKSClassDeclaration()!!,
          scopes = scopes,
        )
      }

    val contributedTypesByAnnotation =
      mutableMapOf<FqName, MutableMap<KSType, MutableSet<ContributedType>>>()
    for (contributed in contributedTypes) {
      contributed.reference.resolvableAnnotations
        .forEach { annotation ->
          val type = annotation.annotationType
            .contextualToClassName().fqName
          if (type !in CONTRIBUTION_ANNOTATIONS) return@forEach
          for (scope in contributed.scopes) {
            contributedTypesByAnnotation.getOrPut(type, ::mutableMapOf)
              .getOrPut(scope, ::mutableSetOf)
              .add(contributed)
          }
        }
    }
    return contributedTypesByAnnotation
  }

  data class ContributedType(
    val baseName: String,
    val reference: KSClassDeclaration,
    val scopes: Set<KSType>,
  )

  fun endRound() {
    hintCacheWarmer = null
    roundStarted = false
    log(_hintCache.statsString())
    _hintCache.clear()
  }

  /**
   * Returns a sequence of contributed classes from the dependency graph. Note that the result
   * includes inner classes already.
   */
  fun findContributedClasses(
    annotation: FqName,
    scope: KSType?,
  ): Sequence<KSClassDeclaration> {
    return trace("Processing contributed classes for ${annotation.shortName().asString()}") {
      val typesByScope = hintCache[annotation] ?: emptyMap()
      typesByScope.filterKeys { scope == null || it == scope }
        .values
        .asSequence()
        .flatten()
        .map { it.reference }
        .distinctBy { it.qualifiedName?.asString() }
        .onEach { clazz ->
          if (clazz.origin == Origin.KOTLIN_LIB || clazz.origin == Origin.JAVA_LIB) {
            externalContributions.add(clazz.fqName)
          }
        }
    }
  }

  companion object {
    private val CONTRIBUTION_ANNOTATIONS = setOf(
      contributesToFqName,
      contributesSubcomponentFqName,
    )
  }

  private sealed class GeneratedProperty(
    val declaration: KSPropertyDeclaration,
    val baseName: String,
  ) {
    class ReferenceProperty(
      declaration: KSPropertyDeclaration,
      baseName: String,
    ) : GeneratedProperty(declaration, baseName)

    class ScopeProperty(
      declaration: KSPropertyDeclaration,
      baseName: String,
    ) : GeneratedProperty(declaration, baseName)

    companion object {
      fun from(declaration: KSPropertyDeclaration): GeneratedProperty? {
        // For each contributed hint there are several properties, e.g. the reference itself
        // and the scopes. Group them by their common name without the suffix.
        val name = declaration.simpleName.asString()

        return when {
          name.endsWith(REFERENCE_SUFFIX) ->
            ReferenceProperty(declaration, name.substringBeforeLast(REFERENCE_SUFFIX))
          name.contains(SCOPE_SUFFIX) -> {
            // The old scope hint didn't have a number. Now that there can be multiple scopes
            // we append a number for all scopes, but we still need to support the old format.
            val indexString = name.substringAfterLast(SCOPE_SUFFIX)
            if (indexString.toIntOrNull() != null || indexString.isEmpty()) {
              ScopeProperty(declaration, name.substringBeforeLast(SCOPE_SUFFIX))
            } else {
              null
            }
          }
          else -> null
        }
      }
    }
  }

  /**
   * Finds the applicable parent component interface (if any) contributed to this component.
   */
  fun findParentComponentInterface(
    resolver: Resolver,
    componentClass: KSClassDeclaration,
    creatorClass: KSClassDeclaration?,
    parentScopeType: KSType?,
  ): KSClassDeclaration? = trace(
    "Finding parent component interface for ${componentClass.simpleName.asString()}",
  ) {
    val fqName = componentClass.fqName

    // Can't use getOrPut because it doesn't differentiate between absent and null
    if (fqName in parentComponentCache) {
      parentComponentCache.hit()
      return parentComponentCache[fqName]?.let { resolver.getClassDeclarationByName(it.asString()) }
    } else {
      parentComponentCache.miss()
    }

    val contributedInnerComponentInterfaces = componentClass
      .declarations
      .filterIsInstance<KSClassDeclaration>()
      .filter(KSClassDeclaration::isInterface)
      .filter { nestedClass ->
        nestedClass.resolvableAnnotations
          .any {
            it.fqName == contributesToFqName && (if (parentScopeType != null) it.scope() == parentScopeType else true)
          }
      }
      .toList()

    val componentInterface = when (contributedInnerComponentInterfaces.size) {
      0 -> {
        parentComponentCache[fqName] = null
        return null
      }
      1 -> contributedInnerComponentInterfaces[0]
      else -> throw KspAnvilException(
        node = componentClass,
        message = "Expected zero or one parent component interface within " +
          "${componentClass.fqName} being contributed to the parent scope.",
      )
    }

    val callables = trace("Finding overridable parent component callables") {
      overridableParentComponentCallables(
        resolver,
        componentInterface,
        componentClass.fqName,
        creatorClass?.fqName,
      )
    }

    when (callables.count()) {
      0 -> {
        parentComponentCache[fqName] = null
        return null
      }
      1 -> {
        // This is ok
      }
      else -> throw KspAnvilException(
        node = componentClass,
        message = "Expected zero or one function returning the " +
          "subcomponent ${componentClass.fqName}.",
      )
    }

    parentComponentCache[fqName] = componentInterface.fqName
    return componentInterface
  }

  /**
   * Returns a list of overridable parent component callables from a given [parentComponent]
   * for the given [targetReturnType]. This can include both functions and properties.
   */
  fun overridableParentComponentCallables(
    resolver: Resolver,
    parentComponent: KSClassDeclaration,
    targetReturnType: FqName,
    creatorClass: FqName?,
  ): List<KSCallable> {
    val fqName = parentComponent.fqName

    // Can't use getOrPut because it doesn't differentiate between absent and null
    if (fqName in overridableParentComponentCallableCache) {
      overridableParentComponentCallableCache.hit()
      return overridableParentComponentCallableCache.getValue(
        fqName,
      ).map { it.materialize(resolver) }
    } else {
      overridableParentComponentCallableCache.miss()
    }

    return parentComponent.getAllCallables()
      .filter { it.isAbstract && it.getVisibility() == Visibility.PUBLIC }
      .filter {
        val type = it.type?.resolveKSClassDeclaration()?.fqName ?: return@filter false
        type == targetReturnType || (creatorClass != null && type == creatorClass)
      }
      .toList()
      .also {
        overridableParentComponentCallableCache[fqName] = it.map(KSCallable::toCacheEntry)
      }
  }

  fun dumpStats() {
    log(parentComponentCache.statsString())
    log(overridableParentComponentCallableCache.statsString())
  }
}
