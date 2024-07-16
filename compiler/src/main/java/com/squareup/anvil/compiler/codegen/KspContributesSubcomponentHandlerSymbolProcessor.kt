package com.squareup.anvil.compiler.codegen

import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Visibility
import com.squareup.anvil.annotations.ContributesSubcomponent
import com.squareup.anvil.annotations.MergeSubcomponent
import com.squareup.anvil.annotations.internal.InternalContributedSubcomponentMarker
import com.squareup.anvil.compiler.BINDING_MODULE_SUFFIX
import com.squareup.anvil.compiler.BindingSpec
import com.squareup.anvil.compiler.COMPONENT_PACKAGE_PREFIX
import com.squareup.anvil.compiler.ClassScannerKsp
import com.squareup.anvil.compiler.KspContributionMerger
import com.squareup.anvil.compiler.PARENT_COMPONENT
import com.squareup.anvil.compiler.codegen.ksp.AnvilSymbolProcessor
import com.squareup.anvil.compiler.codegen.ksp.KspAnvilException
import com.squareup.anvil.compiler.codegen.ksp.classId
import com.squareup.anvil.compiler.codegen.ksp.declaringClass
import com.squareup.anvil.compiler.codegen.ksp.exclude
import com.squareup.anvil.compiler.codegen.ksp.find
import com.squareup.anvil.compiler.codegen.ksp.fqName
import com.squareup.anvil.compiler.codegen.ksp.getSymbolsWithAnnotations
import com.squareup.anvil.compiler.codegen.ksp.isAnnotationPresent
import com.squareup.anvil.compiler.codegen.ksp.isDaggerScope
import com.squareup.anvil.compiler.codegen.ksp.isInterface
import com.squareup.anvil.compiler.codegen.ksp.modules
import com.squareup.anvil.compiler.codegen.ksp.parentScope
import com.squareup.anvil.compiler.codegen.ksp.replaces
import com.squareup.anvil.compiler.codegen.ksp.resolveKSClassDeclaration
import com.squareup.anvil.compiler.codegen.ksp.returnTypeOrNull
import com.squareup.anvil.compiler.codegen.ksp.scope
import com.squareup.anvil.compiler.contributesSubcomponentFqName
import com.squareup.anvil.compiler.contributesToFqName
import com.squareup.anvil.compiler.daggerBindingModuleSpec
import com.squareup.anvil.compiler.internal.asClassName
import com.squareup.anvil.compiler.internal.createAnvilSpec
import com.squareup.anvil.compiler.internal.joinSimpleNamesAndTruncate
import com.squareup.anvil.compiler.internal.reference.asClassId
import com.squareup.anvil.compiler.internal.safePackageString
import com.squareup.anvil.compiler.mergeComponentFqName
import com.squareup.anvil.compiler.mergeInterfacesFqName
import com.squareup.anvil.compiler.mergeSubcomponentFqName
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier.ABSTRACT
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.toAnnotationSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name

/**
 * Looks for `@MergeComponent`, `@MergeSubcomponent` or `@MergeModules` annotations and generates
 * the actual contributed subcomponents that specified these scopes as parent scope, e.g.
 *
 * ```
 * @MergeComponent(Unit::class)
 * interface ComponentInterface
 *
 * @ContributesSubcomponent(Any::class, parentScope = Unit::class)
 * interface SubcomponentInterface
 * ```
 * For this code snippet the code generator would generate:
 * ```
 * @MergeSubcomponent(Any::class)
 * interface SubcomponentInterfaceAnvilSubcomponent
 * ```
 */
internal class KspContributesSubcomponentHandlerSymbolProcessor(
  override val env: SymbolProcessorEnvironment,
  private val classScanner: ClassScannerKsp,
) : AnvilSymbolProcessor() {

  /**
   * Detected triggers that trigger events with [contributions]
   */
  private val triggers = LinkedHashSet<Trigger>()

  /**
   * [@ContributesSubcomponent][ContributesSubcomponent]-annotated contributions detected.
   * Computed multiple times, once from the classpath and n+ times from processing rounds.
   */
  private val contributions = mutableSetOf<Contribution>()

  private val replacedReferences = mutableSetOf<KSClassDeclaration>()
  private val processedEvents = mutableSetOf<GenerateCodeEvent>()

  private var isFirstRound = true
  private var hasComputedEventsThisRound = false
  private val pendingEvents = mutableListOf<GenerateCodeEvent>()

  fun hasPendingEvents(): Boolean = pendingEvents.isNotEmpty()

  override fun processChecked(resolver: Resolver): List<KSAnnotated> {
    computePendingEvents(resolver)

    if (pendingEvents.isEmpty()) {
      return emptyList()
    }

    pendingEvents
      .also { processedEvents += it }
      .map { generateCodeEvent ->
        val contribution = generateCodeEvent.contribution
        val generatedAnvilSubcomponent = generateCodeEvent.generatedAnvilSubcomponent

        val generatedClassName = generatedAnvilSubcomponent.asClassName()
        val generatedPackage = generatedAnvilSubcomponent.packageFqName.asString()
        val componentClassSimpleName = generatedAnvilSubcomponent.relativeClassName.asString()

        val factoryClass = findFactoryClass(contribution)

        val contributionClassName = contribution.clazz.toClassName()
        val spec = FileSpec.createAnvilSpec(generatedPackage, componentClassSimpleName) {
          TypeSpec
            .interfaceBuilder(componentClassSimpleName)
            .addSuperinterface(contributionClassName)
            .addAnnotation(
              AnnotationSpec
                .builder(MergeSubcomponent::class)
                .addMember("scope = %T::class", contribution.scope.toClassName())
                .apply {
                  fun addClassArrayMember(
                    name: String,
                    references: List<KSClassDeclaration>,
                  ) {
                    if (references.isNotEmpty()) {
                      val classes = references.map(KSClassDeclaration::toClassName)
                      val template = classes
                        .joinToString(prefix = "[", postfix = "]") { "%T::class" }

                      addMember("$name = $template", *classes.toTypedArray<ClassName>())
                    }
                  }

                  addClassArrayMember("modules", contribution.annotation.modules())
                  addClassArrayMember("exclude", contribution.annotation.exclude())
                }
                .build(),
            )
            .addAnnotations(
              contribution.clazz.annotations
                .filter(KSAnnotation::isDaggerScope)
                .map(KSAnnotation::toAnnotationSpec)
                .asIterable(),
            )
            .apply {
              val parentComponentInterface =
                findParentComponentInterface(contribution, factoryClass?.originalReference)
              addAnnotation(
                AnnotationSpec.builder(InternalContributedSubcomponentMarker::class)
                  .addMember("originClass = %T::class", contributionClassName)
                  .apply {
                    if (factoryClass != null) {
                      addMember(
                        "componentFactory = %T::class",
                        factoryClass.originalReference.toClassName(),
                      )
                    } else {
                      // The contributor is either the parent component interface defined in the
                      // @ContributesSubcomponent-annotated class OR (if there isn't one there) the
                      // one we'll generate for them in this class.
                      val contributor = parentComponentInterface?.componentInterface
                        ?: generatedClassName.nestedClass(PARENT_COMPONENT)
                      addMember("contributor = %T::class", contributor)
                    }
                  }
                  .build(),
              )

              addType(
                daggerBindingModuleSpec(
                  BINDING_MODULE_SUFFIX,
                  listOf(BindingSpec(impl = generatedClassName, boundType = contributionClassName)),
                ),
              )

              if (parentComponentInterface == null) {
                // No parent component interface is specified, so generate either:
                // - A parent component that exposes the factory (if one is specified)
                // - A parent component that exposes the contributed subcomponent to the parent scope
                addType(
                  generateParentComponent(
                    origin = contributionClassName,
                    factoryClass = factoryClass,
                  ),
                )
              }
            }
            .addOriginatingKSFile(generateCodeEvent.trigger.clazz.containingFile!!)
            .apply {
              if (!classScanner.isExternallyContributed(generateCodeEvent.contribution.clazz)) {
                addOriginatingKSFile(generateCodeEvent.contribution.clazz.containingFile!!)
              }
            }
            .build()
            .also(::addType)
        }

        // Aggregating because we read symbols from the classpath
        spec.writeTo(env.codeGenerator, aggregating = true)
      }

    hasComputedEventsThisRound = false
    pendingEvents.clear()

    return emptyList()
  }

  /**
   * Computes the pending events for this round. This includes finding new triggers and
   * contributions.
   *
   * This function is idempotent per-round.
   *
   * This is exposed internally for access in [KspContributionMerger], which may be running this
   * processor in an encapsulated fashion.
   */
  fun computePendingEvents(resolver: Resolver) {
    if (hasComputedEventsThisRound) {
      // Already computed this round
      return
    } else {
      hasComputedEventsThisRound = true
    }

    if (isFirstRound) {
      isFirstRound = false
      populateInitialContributions(resolver)
    }

    // Find new @MergeComponent (and similar triggers) that would cause generating new code.
    triggers += generationTriggers
      .flatMap { generationTrigger ->
        resolver.getSymbolsWithAnnotation(generationTrigger.asString())
          .filterIsInstance<KSClassDeclaration>()
          .flatMap { annotatedClass ->
            annotatedClass.find(generationTrigger.asString())
              .map { annotation ->
                Trigger(annotatedClass, annotation.scope(), annotation.exclude().toSet())
              }
          }
      }

    // Find new contributed subcomponents in this module. If there's a trigger for them, then we
    // also need to generate code for them later.
    contributions += resolver.getSymbolsWithAnnotations(contributesSubcomponentFqName)
      .map {
        val annotation = it.find(contributesSubcomponentFqName.asString()).single()
        Contribution(annotation)
      }

    // Find all replaced subcomponents and remember them.
    replacedReferences += contributions
      .flatMap { contribution -> contribution.annotation.replaces() }

    for (contribution in contributions) {
      checkReplacedSubcomponentWasNotAlreadyGenerated(contribution.clazz, replacedReferences)
    }

    // Remove any contribution that was replaced by another contribution.
    contributions.removeAll { it.clazz in replacedReferences }

    pendingEvents += contributions
      .flatMap { contribution ->
        triggers
          .filter { trigger ->
            trigger.scope == contribution.parentScopeType && contribution.clazz !in trigger.exclusions
          }
          .map { trigger ->
            GenerateCodeEvent(trigger, contribution)
          }
      }
      // Don't generate code for the same event twice.
      .minus(processedEvents)
  }

  private fun generateParentComponent(
    origin: ClassName,
    factoryClass: FactoryClassHolder?,
  ): TypeSpec {
    return TypeSpec
      .interfaceBuilder(PARENT_COMPONENT)
      .addFunction(
        FunSpec
          .builder(
            name = factoryClass?.let { "create${it.originalReference.fqName.shortName()}" }
              ?: "create${origin.simpleName}",
          )
          .addModifiers(ABSTRACT)
          .apply {
            if (factoryClass != null) {
              returns(factoryClass.originalReference.toClassName())
            } else {
              returns(origin)
            }
          }
          .build(),
      )
      .build()
  }

  private fun findParentComponentInterface(
    contribution: Contribution,
    factoryClass: KSClassDeclaration?,
  ): ParentComponentInterfaceHolder? {
    val contributedInnerComponentInterfaces = contribution.clazz
      .declarations
      .filterIsInstance<KSClassDeclaration>()
      .filter(KSClassDeclaration::isInterface)
      .filter { nestedClass ->
        nestedClass.annotations
          .any {
            it.fqName == contributesToFqName && it.scope() == contribution.parentScopeType
          }
      }
      .toList()

    val componentInterface = when (contributedInnerComponentInterfaces.size) {
      0 -> return null
      1 -> contributedInnerComponentInterfaces[0]
      else -> throw KspAnvilException(
        node = contribution.clazz,
        message = "Expected zero or one parent component interface within " +
          "${contribution.clazz.fqName} being contributed to the parent scope.",
      )
    }

    val functions = componentInterface.getAllFunctions()
      .filter { it.isAbstract && it.getVisibility() == Visibility.PUBLIC }
      .filter {
        val returnType = it.returnTypeOrNull()?.resolveKSClassDeclaration() ?: return@filter false
        returnType.fqName == contribution.clazz.fqName || (factoryClass != null && returnType.fqName == factoryClass.fqName)
      }
      .toList()

    when (functions.size) {
      0 -> return null
      1 -> {
        // This is ok
      }
      else -> throw KspAnvilException(
        node = contribution.clazz,
        message = "Expected zero or one function returning the " +
          "subcomponent ${contribution.clazz.fqName}.",
      )
    }

    return ParentComponentInterfaceHolder(componentInterface)
  }

  private fun findFactoryClass(
    contribution: Contribution,
  ): FactoryClassHolder? {
    val contributionClassName = contribution.clazz.toClassName()

    val contributedFactories = contribution.clazz
      .declarations
      .filterIsInstance<KSClassDeclaration>()
      .filter { nestedClass ->
        nestedClass.isAnnotationPresent<ContributesSubcomponent.Factory>()
      }
      .onEach { factory ->
        if (!factory.isInterface() && !factory.isAbstract()) {
          throw KspAnvilException(
            node = factory,
            message = "A factory must be an interface or an abstract class.",
          )
        }

        val factoryFqcn = factory.qualifiedName?.asString()
        val implementingType = factory.asType(emptyList())
        val createComponentFunctions = factory.getAllFunctions()
          .filter(KSFunctionDeclaration::isAbstract)
          .filter { function ->
            // Have to check the parent because asMemberOf() isn't safe to call with the
            // direct parent
            val returnTypeToCheck =
              if (function.parentDeclaration?.qualifiedName?.asString() == factoryFqcn) {
                function.returnTypeOrNull()
              } else {
                function.asMemberOf(implementingType).returnTypeOrNull()
              }
            returnTypeToCheck
              ?.resolveKSClassDeclaration()
              ?.toClassName() == contributionClassName
          }
          .toList()

        if (createComponentFunctions.size != 1) {
          throw KspAnvilException(
            node = factory,
            message = "A factory must have exactly one abstract function returning the " +
              "subcomponent $contributionClassName.",
          )
        }
      }
      .toList()

    val originalReference = when (contributedFactories.size) {
      0 -> return null
      1 -> contributedFactories[0]
      else -> throw KspAnvilException(
        node = contribution.clazz,
        message = "Expected zero or one factory within $contributionClassName.",
      )
    }

    return FactoryClassHolder(
      originalReference = originalReference,
    )
  }

  private fun checkReplacedSubcomponentWasNotAlreadyGenerated(
    contributedReference: KSClassDeclaration,
    replacedReferences: Collection<KSClassDeclaration>,
  ) {
    replacedReferences.forEach { replacedReference ->
      if (processedEvents.any { it.contribution.clazz == replacedReference }) {
        throw KspAnvilException(
          node = contributedReference,
          message = "${contributedReference.fqName} tries to replace " +
            "${replacedReference.fqName}, but the code for ${replacedReference.fqName} was " +
            "already generated. This is not supported.",
        )
      }
    }
  }

  private fun populateInitialContributions(resolver: Resolver) {
    // Find all contributed subcomponents from precompiled dependencies and generate the
    // necessary code eventually if there's a trigger.
    contributions += classScanner
      .findContributedClasses(
        resolver = resolver,
        annotation = contributesSubcomponentFqName,
        scope = null,
      )
      .map { clazz ->
        Contribution(
          annotation = clazz.annotations.single { it.fqName == contributesSubcomponentFqName },
        )
      }

    // Find all replaced subcomponents from precompiled dependencies.
    replacedReferences += contributions
      .flatMap { contribution ->
        contribution.clazz
          .annotations.single { it.fqName == contributesSubcomponentFqName }
          .replaces()
      }
  }

  private companion object {
    val generationTriggers = sequenceOf(
      mergeComponentFqName,
      mergeSubcomponentFqName,
      // Note that we don't include @MergeModules, because we would potentially generate
      // components twice. @MergeInterfaces and @MergeModules are doing separately what
      // @MergeComponent is doing at once.
      mergeInterfacesFqName,
    )
  }

  private class Trigger(
    val clazz: KSClassDeclaration,
    val scope: KSType,
    val exclusions: Set<KSClassDeclaration>,
  ) {

    val clazzFqName = clazz.fqName

    override fun toString(): String {
      return "Trigger(clazz=$clazzFqName, scope=${scope.fqName})"
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as Trigger

      if (scope != other.scope) return false
      if (clazzFqName != other.clazzFqName) return false

      return true
    }

    override fun hashCode(): Int {
      var result = scope.hashCode()
      result = 31 * result + clazzFqName.hashCode()
      return result
    }
  }

  private class Contribution(val annotation: KSAnnotation) {
    val clazz = annotation.declaringClass
    val scope = annotation.scope()
    val parentScopeType = annotation.parentScope().asType(emptyList())

    override fun toString(): String {
      return "Contribution(class=$clazz, scope=$scope, parentScope=$parentScopeType)"
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as Contribution

      if (scope != other.scope) return false
      if (parentScopeType != other.parentScopeType) return false
      if (clazz != other.clazz) return false

      return true
    }

    override fun hashCode(): Int {
      var result = scope.hashCode()
      result = 31 * result + parentScopeType.hashCode()
      result = 31 * result + clazz.hashCode()
      return result
    }
  }

  private data class GenerateCodeEvent(
    val trigger: Trigger,
    val contribution: Contribution,
  ) {
    val generatedAnvilSubcomponent = contribution.clazz.classId
      .generatedAnvilSubcomponentClassId(trigger.clazz.classId)
  }

  private class ParentComponentInterfaceHolder(componentInterface: KSClassDeclaration) {
    val componentInterface = componentInterface.toClassName()
  }

  private class FactoryClassHolder(val originalReference: KSClassDeclaration)
}

/**
 * Returns the Anvil subcomponent that will be generated for a class annotated with
 * `ContributesSubcomponent`.
 */
internal fun ClassId.generatedAnvilSubcomponentClassId(parentClass: ClassId): ClassId {
  // Encode the parent class name in the package rather than the class name itself. This avoids
  // issues with too long class names. Dagger will generate subcomponents as inner classes and
  // deep hierarchies will be a problem. See https://github.com/google/dagger/issues/421
  //
  // To avoid potential issues, encode the parent class name in the package and keep the generated
  // class name short.
  val parentClassPackageSuffix = if (parentClass.relativeClassName.isRoot) {
    "root"
  } else {
    parentClass.relativeClassName.asString().lowercase()
  }

  // Note that use the package from the parent class and not our actual subcomponent. This is
  // necessary to avoid conflicts as well.
  val parentPackageName = parentClass.packageFqName
    .safePackageString(dotPrefix = false, dotSuffix = true) + parentClassPackageSuffix

  val packageFqName = if (parentPackageName.startsWith(COMPONENT_PACKAGE_PREFIX)) {
    // This happens if the parent is generated by Anvil itself. Avoid nesting too deeply.
    parentPackageName
  } else {
    "$COMPONENT_PACKAGE_PREFIX.$parentPackageName"
  }

  val segments = relativeClassName.pathSegments()
  return ClassName(
    packageName = packageFqName,
    simpleNames = segments.map(Name::asString),
  )
    .joinSimpleNamesAndTruncate(
      hashParams = listOf(parentClass),
      separator = "_",
      innerClassLength = PARENT_COMPONENT.length,
    )
    .asClassId(local = false)
}
