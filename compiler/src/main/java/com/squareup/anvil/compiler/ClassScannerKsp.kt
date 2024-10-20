package com.squareup.anvil.compiler

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.Origin
import com.google.devtools.ksp.symbol.Visibility
import com.squareup.anvil.annotations.internal.InternalAnvilHintMarker
import com.squareup.anvil.compiler.ClassScannerKsp.GeneratedProperty.ReferenceProperty
import com.squareup.anvil.compiler.ClassScannerKsp.GeneratedProperty.ScopeProperty
import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.codegen.ksp.KSCallable
import com.squareup.anvil.compiler.codegen.ksp.KspTracer
import com.squareup.anvil.compiler.codegen.ksp.getAllCallables
import com.squareup.anvil.compiler.codegen.ksp.isAbstract
import com.squareup.anvil.compiler.codegen.ksp.trace
import com.squareup.anvil.compiler.codegen.ksp.type
import com.squareup.anvil.compiler.internal.fqName
import com.squareup.anvil.compiler.internal.ksp.KspAnvilException
import com.squareup.anvil.compiler.internal.ksp.contextualToClassName
import com.squareup.anvil.compiler.internal.ksp.fqName
import com.squareup.anvil.compiler.internal.ksp.getClassDeclarationByName
import com.squareup.anvil.compiler.internal.ksp.isInterface
import com.squareup.anvil.compiler.internal.ksp.parentScope
import com.squareup.anvil.compiler.internal.ksp.resolvableAnnotations
import com.squareup.anvil.compiler.internal.ksp.resolveKSClassDeclaration
import com.squareup.anvil.compiler.internal.ksp.scopeClassName
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ksp.toClassName
import org.jetbrains.kotlin.name.FqName

internal class ClassScannerKsp(
  tracer: KspTracer,
) : KspTracer by tracer {
  private val _hintCache =
    RecordingCache<FqName, MutableMap<ClassName, MutableSet<ContributedType>>>("Generated Property")

  private val parentComponentCache = RecordingCache<FqName, FqName?>("ParentComponent")

  private val overridableParentComponentCallableCache =
    RecordingCache<FqName, List<KSCallable.CacheEntry>>("Overridable ParentComponent Callable")

  /**
   * Externally-contributed contributions, which are important to track so that we don't try to
   * add originating files for them when generating code.
   */
  private val externalContributions = mutableSetOf<ClassName>()

  private var classpathHintCacheWarmed = false
  private var inRoundClasspathHintCacheWarmed = false
  private var ensureInRoundHintsCaptured = false
  private var roundStarted = false
  private var roundResolver: Resolver? = null
  private val resolver: Resolver get() = roundResolver ?: error("Round not started!")
  private var round = 0

  fun isExternallyContributed(declaration: KSClassDeclaration): Boolean {
    return declaration.toClassName() in externalContributions
  }

  /**
   * If called, instructs this scanner to capture in-round hints. Should usually be called if the
   * consuming processor knows it will have deferred elements in a future round.
   */
  fun ensureInRoundHintsCaptured() {
    ensureInRoundHintsCaptured = true
  }

  private fun KSTypeReference.resolveKClassType(): KSType {
    return resolve()
      .arguments.single().type!!.resolve()
  }

  /**
   * In order to limit classpath scanning, we cache the contributed hints from the classpath once
   * in a KSP-compatible format (i.e. no holding onto symbols). Separately, we annotate hints
   * with [InternalAnvilHintMarker] to pick them up in the current round.
   *
   * [ClassScanningKspProcessor] in turn ensures that any [InternalAnvilHintMarker]-annotated
   * symbols in a given round are passed on to the next round.
   *
   * The end result is every hint is only processed once into our cache.
   */
  @OptIn(KspExperimental::class)
  private fun hintCache(): RecordingCache<FqName, MutableMap<ClassName, MutableSet<ContributedType>>> {
    if (!classpathHintCacheWarmed) {
      val newHints = trace("Warming classpath hint cache") {
        generateHintCache(
          resolver.getDeclarationsFromPackage(HINT_PACKAGE)
            .filterIsInstance<KSPropertyDeclaration>(),
          isClassPathScan = true,
        ).also {
          log(
            "Loaded ${it.values.flatMap { it.values.flatten() }.size} contributed hints from the classpath.",
          )
        }
      }
      mergeNewHints(newHints)
      classpathHintCacheWarmed = true
    }
    findInRoundHints()
    return _hintCache
  }

  private fun findInRoundHints() {
    if (!inRoundClasspathHintCacheWarmed) {
      val newHints = trace("Warming in-round hint cache") {
        generateHintCache(
          resolver.getSymbolsWithAnnotation(internalAnvilHintMarkerClassName.canonicalName)
            .filterIsInstance<KSPropertyDeclaration>(),
          isClassPathScan = false,
        )
      }
      mergeNewHints(newHints)
      inRoundClasspathHintCacheWarmed = true
    }
  }

  fun startRound(resolver: Resolver) {
    if (roundStarted) return
    round++
    roundStarted = true
    roundResolver = resolver
  }

  private fun mergeNewHints(
    newHints: Map<FqName, Map<ClassName, Set<ContributedType>>>,
  ) {
    for ((annotation, hints) in newHints) {
      for ((scope, contributedTypes) in hints) {
        _hintCache.mutate { rawCache ->
          rawCache.getOrPut(annotation, ::mutableMapOf)
            .getOrPut(scope, ::mutableSetOf)
            .addAll(contributedTypes)
        }
      }
    }
  }

  private fun generateHintCache(
    properties: Sequence<KSPropertyDeclaration>,
    isClassPathScan: Boolean,
  ): MutableMap<FqName, MutableMap<ClassName, MutableSet<ContributedType>>> {
    val contributedTypes = properties
      .mapNotNull(GeneratedProperty::from)
      .groupBy(GeneratedProperty::baseName)
      .mapNotNull { (name, properties) ->
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
          .mapToSet {
            it.declaration.type.resolveKClassType()
              .contextualToClassName(it.declaration)
          }

        val declaration = refProp.declaration.type
          .resolveKClassType()
          .resolveKSClassDeclaration()!!

        val className = declaration.toClassName()

        if (isClassPathScan && (declaration.origin == Origin.KOTLIN_LIB || declaration.origin == Origin.JAVA_LIB)) {
          externalContributions += className
        }

        var contributedSubcomponentData: ContributedType.ContributedSubcomponentData? = null
        val contributingAnnotationTypes = mutableSetOf<FqName>()
        val contributesToData = mutableSetOf<ContributedType.ContributesToData>()
        var isDaggerModule = false

        declaration.resolvableAnnotations
          .forEach { annotation ->
            val type = annotation.annotationType
              .contextualToClassName().fqName
            if (type == daggerModuleFqName) {
              isDaggerModule = true
            } else if (type in CONTRIBUTION_ANNOTATIONS) {
              contributingAnnotationTypes += type
              if (type == contributesSubcomponentFqName) {
                val scope = annotation.scopeClassName()
                val parentScope = annotation.parentScope().toClassName()
                contributedSubcomponentData = ContributedType.ContributedSubcomponentData(
                  scope = scope,
                  parentScope = parentScope,
                )
              } else if (type == contributesToFqName) {
                val scope = annotation.scopeClassName()
                contributesToData += ContributedType.ContributesToData(scope = scope)
              }
            }
          }

        if (contributingAnnotationTypes.isEmpty()) return@mapNotNull null

        ContributedType(
          baseName = name,
          className = className,
          scopes = scopes,
          contributingAnnotationTypes = contributingAnnotationTypes,
          isInterface = declaration.isInterface(),
          isDaggerModule = isDaggerModule,
          contributedSubcomponentData = contributedSubcomponentData,
          contributesToData = contributesToData,
        )
      }

    val contributedTypesByAnnotation =
      mutableMapOf<FqName, MutableMap<ClassName, MutableSet<ContributedType>>>()
    for (contributed in contributedTypes) {
      contributed.contributingAnnotationTypes
        .forEach { type ->
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
    val className: ClassName,
    val scopes: Set<ClassName>,
    val contributingAnnotationTypes: Set<FqName>,
    val isInterface: Boolean,
    val isDaggerModule: Boolean,
    val contributedSubcomponentData: ContributedSubcomponentData?,
    val contributesToData: Set<ContributesToData>,
  ) {
    data class ContributesToData(
      val scope: ClassName,
    )

    data class ContributedSubcomponentData(
      val scope: ClassName,
      val parentScope: ClassName,
    )
  }

  fun endRound() {
    // If we generate any hint markers, we need to pass them on to the next round for the class
    // scanner
    if (ensureInRoundHintsCaptured) {
      findInRoundHints()
    }
    roundStarted = false
    roundResolver = null
    inRoundClasspathHintCacheWarmed = false
    ensureInRoundHintsCaptured = false
    log(_hintCache.statsString())
  }

  /**
   * Returns a sequence of contributed classes from the dependency graph. Note that the result
   * includes inner classes already.
   */
  fun findContributedClasses(
    annotation: FqName,
    scope: ClassName?,
  ): Sequence<ContributedType> {
    return trace("Processing contributed classes for ${annotation.shortName().asString()}") {
      val typesByScope = hintCache()[annotation] ?: emptyMap()
      typesByScope.filterKeys {
        scope == null || it == scope
      }
        .values
        .asSequence()
        .flatten()
        .distinctBy { it.className }
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
    parentScopeType: ClassName?,
  ): KSClassDeclaration? = trace(
    "Finding parent component interface for ${componentClass.simpleName.asString()}",
  ) {
    val fqName = componentClass.fqName

    // Can't use getOrPut because it doesn't differentiate between absent and null
    if (fqName in parentComponentCache) {
      parentComponentCache.hit()
      return parentComponentCache[fqName]?.let { resolver.getClassDeclarationByName(it) }
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
            it.fqName == contributesToFqName && (if (parentScopeType != null) it.scopeClassName() == parentScopeType else true)
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
