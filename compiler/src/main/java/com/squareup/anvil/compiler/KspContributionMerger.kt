package com.squareup.anvil.compiler

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSName
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.KSValueArgument
import com.google.devtools.ksp.symbol.KSVisitor
import com.google.devtools.ksp.symbol.Visibility
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.anvil.annotations.MergeComponent
import com.squareup.anvil.annotations.MergeSubcomponent
import com.squareup.anvil.annotations.compat.MergeInterfaces
import com.squareup.anvil.annotations.compat.MergeModules
import com.squareup.anvil.annotations.internal.InternalBindingMarker
import com.squareup.anvil.annotations.internal.InternalContributedSubcomponentMarker
import com.squareup.anvil.annotations.internal.InternalMergedTypeMarker
import com.squareup.anvil.compiler.codegen.KspContributesSubcomponentHandlerSymbolProcessor
import com.squareup.anvil.compiler.codegen.KspMergeAnnotationsCheckSymbolProcessor
import com.squareup.anvil.compiler.codegen.generatedAnvilSubcomponentClassId
import com.squareup.anvil.compiler.codegen.ksp.AnvilSymbolProcessor
import com.squareup.anvil.compiler.codegen.ksp.KspAnvilException
import com.squareup.anvil.compiler.codegen.ksp.argumentAt
import com.squareup.anvil.compiler.codegen.ksp.atLeastOneAnnotation
import com.squareup.anvil.compiler.codegen.ksp.classId
import com.squareup.anvil.compiler.codegen.ksp.declaringClass
import com.squareup.anvil.compiler.codegen.ksp.exclude
import com.squareup.anvil.compiler.codegen.ksp.find
import com.squareup.anvil.compiler.codegen.ksp.findAll
import com.squareup.anvil.compiler.codegen.ksp.fqName
import com.squareup.anvil.compiler.codegen.ksp.getKSAnnotationsByType
import com.squareup.anvil.compiler.codegen.ksp.getSymbolsWithAnnotations
import com.squareup.anvil.compiler.codegen.ksp.includes
import com.squareup.anvil.compiler.codegen.ksp.isAnnotationPresent
import com.squareup.anvil.compiler.codegen.ksp.isInterface
import com.squareup.anvil.compiler.codegen.ksp.mergeAnnotations
import com.squareup.anvil.compiler.codegen.ksp.modules
import com.squareup.anvil.compiler.codegen.ksp.parentScope
import com.squareup.anvil.compiler.codegen.ksp.replaces
import com.squareup.anvil.compiler.codegen.ksp.resolveKSClassDeclaration
import com.squareup.anvil.compiler.codegen.ksp.returnTypeOrNull
import com.squareup.anvil.compiler.codegen.ksp.scope
import com.squareup.anvil.compiler.codegen.ksp.superTypesExcludingAny
import com.squareup.anvil.compiler.codegen.ksp.toFunSpec
import com.squareup.anvil.compiler.internal.asClassName
import com.squareup.anvil.compiler.internal.capitalize
import com.squareup.anvil.compiler.internal.createAnvilSpec
import com.squareup.anvil.compiler.internal.findRawType
import com.squareup.anvil.compiler.internal.mergedClassName
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier.ABSTRACT
import com.squareup.kotlinpoet.KModifier.OVERRIDE
import com.squareup.kotlinpoet.NOTHING
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.joinToCode
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.toAnnotationSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import dagger.Binds
import dagger.Component
import dagger.Module
import dagger.Subcomponent
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import kotlin.reflect.KClass

/**
 * A [com.google.devtools.ksp.processing.SymbolProcessor] that performs the two types of merging
 * Anvil supports.
 *
 * 1. **Module merging**: This step sources from `@MergeComponent`, `@MergeSubcomponent`, and
 * `@MergeModules` to merge all contributed modules on the classpath to the generated element.
 *
 * 2. **Interface merging**: This step finds all contributed component interfaces and adds them
 * as supertypes to generated Dagger components from interfaces annotated with `@MergeComponent`
 * or `@MergeSubcomponent`. This also supports arbitrary interface merging on interfaces annotated
 * with `@MergeInterfaces`.
 */
internal class KspContributionMerger(
  override val env: SymbolProcessorEnvironment,
  private val classScanner: ClassScannerKsp,
  private val contributesSubcomponentHandler: KspContributesSubcomponentHandlerSymbolProcessor,
) : AnvilSymbolProcessor() {

  private val contributedSubcomponentDataCache =
    mutableMapOf<FqName, ContributedSubcomponentData?>()

  override fun processChecked(
    resolver: Resolver,
  ): List<KSAnnotated> {
    // If there's any remaining `@Contributes*`-annotated classes, defer to a later round
    val contributingAnnotations = resolver.getSymbolsWithAnnotations(
      contributesBindingFqName,
      contributesMultibindingFqName,
      contributesSubcomponentFqName,
    ).toList()

    var shouldDefer = contributingAnnotations.isNotEmpty()

    if (!shouldDefer) {
      // If any @InternalContributedSubcomponentMarker-annotated classes are generated with
      // parent scopes, we need to defer one more round to let their code gen happen if they haven't
      // generated their contributed subcomponents yet.
      contributesSubcomponentHandler.computePendingEvents(resolver)
      if (contributesSubcomponentHandler.hasPendingEvents()) {
        shouldDefer = true
      }
    }

    contributesSubcomponentHandler.process(resolver)

    // Don't defer if it's both ContributesTo and MergeModules/MergeInterfaces. In this case,
    // we need to process now and just point at what will eventually be generated

    // Mapping of scopes to contributing interfaces in this round
    val contributedInterfacesInRound = mutableMapOf<KSType, MutableList<KSClassDeclaration>>()

    // Mapping of scopes to contributing modules in this round
    val contributedModulesInRound = mutableMapOf<KSType, MutableList<KSClassDeclaration>>()

    resolver.getSymbolsWithAnnotations(contributesToFqName)
      .filterIsInstance<KSClassDeclaration>()
      .forEach { contributedTypeInRound ->
        val contributesToScopes =
          contributedTypeInRound.getKSAnnotationsByType(ContributesTo::class)
            .map(KSAnnotation::scope)
            .toSet()
        if (contributesToScopes.isNotEmpty()) {
          val isModule = contributedTypeInRound.isAnnotationPresent<Module>() ||
            contributedTypeInRound.isAnnotationPresent<MergeModules>()
          if (isModule) {
            for (scope in contributesToScopes) {
              contributedModulesInRound.getOrPut(scope, ::mutableListOf)
                .add(contributedTypeInRound)
            }
            return@forEach
          }

          if (contributedTypeInRound.isInterface()) {
            for (scope in contributesToScopes) {
              contributedInterfacesInRound.getOrPut(scope, ::mutableListOf)
                .add(contributedTypeInRound)
            }
          }
        }
      }

    val deferred = resolver.getSymbolsWithAnnotations(
      mergeComponentFqName,
      mergeSubcomponentFqName,
      mergeModulesFqName,
      mergeInterfacesFqName,
    ).filterIsInstance<KSClassDeclaration>()
      .validate { deferred ->
        return deferred
      }
      .also { mergeAnnotatedTypes ->
        if (shouldDefer) {
          return mergeAnnotatedTypes
        }
      }
      .mapNotNull { annotated ->
        processClass(
          resolver,
          annotated,
          contributedInterfacesInRound,
          contributedModulesInRound,
        )
      }

    return deferred
  }

  /**
   * Returns non-null if the given [mergeAnnotatedClass] could not be processed.
   */
  private fun processClass(
    resolver: Resolver,
    mergeAnnotatedClass: KSClassDeclaration,
    contributedInterfacesInRound: Map<KSType, List<KSClassDeclaration>>,
    contributedModulesInRound: Map<KSType, List<KSClassDeclaration>>,
  ): KSAnnotated? {
    val mergeComponentAnnotations = mergeAnnotatedClass
      .findAll(mergeComponentFqName.asString(), mergeSubcomponentFqName.asString())

    val mergeModulesAnnotations = mergeAnnotatedClass
      .findAll(mergeModulesFqName.asString())

    val daggerMergeAnnotations = mergeComponentAnnotations + mergeModulesAnnotations

    val isModule = mergeModulesAnnotations.isNotEmpty()

    val generatedComponentClassName = mergeAnnotatedClass.toClassName()
      .mergedClassName()

    val contributedSubcomponentData =
      mergeAnnotatedClass.getOrComputeContributedSubcomponentData()

    val originatingDeclarations = mutableListOf<KSClassDeclaration>()

    val creator = if (mergeComponentAnnotations.isNotEmpty()) {
      // if we have contributed data, use the factory information from there
      var resolvedCreator: Creator? = null
      if (contributedSubcomponentData != null) {
        val creatorDecl = contributedSubcomponentData.resolveCreatorDeclaration(resolver)
        if (creatorDecl != null) {
          originatingDeclarations += creatorDecl
          resolvedCreator = Creator.fromDeclaration(
            declaration = creatorDecl,
            mergeAnnotatedClassName = mergeAnnotatedClass.toClassName(),
            generatedComponentClassName = generatedComponentClassName,
          )
        }
      }
      resolvedCreator ?: mergeAnnotatedClass.declarations
        .filterIsInstance<KSClassDeclaration>()
        .firstNotNullOfOrNull { nestedClass ->
          Creator.fromDeclaration(
            nestedClass,
            mergeAnnotatedClass.toClassName(),
            generatedComponentClassName,
          )
        }
    } else {
      null
    }

    val daggerAnnotation = if (daggerMergeAnnotations.isNotEmpty()) {
      generateDaggerAnnotation(
        annotations = daggerMergeAnnotations,
        generatedComponentClassName = generatedComponentClassName,
        contributedSubcomponentData = contributedSubcomponentData,
        originatingDeclarations = originatingDeclarations,
        resolver = resolver,
        declaration = mergeAnnotatedClass,
        isModule = isModule,
        contributedModulesInRound = contributedModulesInRound,
      )
    } else {
      null
    }

    val mergeInterfacesAnnotations = mergeAnnotatedClass
      .findAll(mergeInterfacesFqName.asString())

    val interfaceMergerAnnotations = mergeComponentAnnotations + mergeInterfacesAnnotations

    val contributedInterfaces = if (interfaceMergerAnnotations.isNotEmpty()) {
      if (!mergeAnnotatedClass.isInterface()) {
        throw KspAnvilException(
          node = mergeAnnotatedClass,
          message = "Dagger components (or classes annotated with @MergeInterfaces)" +
            " must be interfaces.",
        )
      }

      contributedInterfaces(
        mergeAnnotations = interfaceMergerAnnotations,
        originatingDeclarations = originatingDeclarations,
        resolver = resolver,
        mergeAnnotatedClass = mergeAnnotatedClass,
        contributedInterfacesInRound = contributedInterfacesInRound,
      )
    } else {
      null
    }

    val scope = (daggerMergeAnnotations + interfaceMergerAnnotations).first().scope()
    generateMergedClass(
      resolver = resolver,
      scope = scope.toClassName(),
      mergeAnnotatedClass = mergeAnnotatedClass,
      originatingDeclarations = originatingDeclarations,
      contributedSubcomponentData = contributedSubcomponentData,
      generatedComponentClassName = generatedComponentClassName,
      daggerAnnotation = daggerAnnotation,
      creator = creator,
      contributedInterfaces = contributedInterfaces,
      isModule = isModule,
    )
    return null
  }

  private fun generateDaggerAnnotation(
    annotations: List<KSAnnotation>,
    generatedComponentClassName: ClassName,
    contributedSubcomponentData: ContributedSubcomponentData?,
    originatingDeclarations: MutableList<KSClassDeclaration>,
    resolver: Resolver,
    declaration: KSClassDeclaration,
    isModule: Boolean,
    contributedModulesInRound: Map<KSType, List<KSClassDeclaration>>,
  ): AnnotationSpec {
    val daggerAnnotationClassName = annotations[0].daggerAnnotationClassName

    val scopes = annotations.map(KSAnnotation::scope)
    // Any modules that are @MergeModules, we need to include their generated modules instead
    val predefinedModules = if (isModule) {
      annotations.flatMap(KSAnnotation::includes)
    } else {
      annotations.flatMap(KSAnnotation::modules)
    }.map { includedModules ->
      if (includedModules.isAnnotationPresent<MergeModules>()) {
        val expected = includedModules.toClassName().mergedClassName().canonicalName
        resolver.getClassDeclarationByName(expected)!!.also {
          originatingDeclarations += it
        }
      } else {
        includedModules
      }.toClassName()
    }
      .let {
        if (contributedSubcomponentData != null) {
          it + declaration.toClassName().nestedClass(BINDING_MODULE_SUFFIX)
        } else {
          it
        }
      }
      // Every generated merged component will have a binding module to map it back to the
      // root type
      .plus(generatedComponentClassName.nestedClass(BINDING_MODULE_SUFFIX))

    val allContributesAnnotations: List<KSAnnotation> = annotations
      .asSequence()
      .flatMap { annotation ->
        val scope = annotation.scope()
        classScanner
          .findContributedClasses(
            resolver = resolver,
            annotation = contributesToFqName,
            scope = scope,
          )
          .plus(
            contributorsInRound(resolver, contributedModulesInRound, scope, isModule = true),
          )
      }
      .flatMap { contributedClass ->
        contributedClass
          .find(annotationName = contributesToFqName.asString())
          .filter { it.scope() in scopes }
      }
      .filter { contributesAnnotation ->
        val contributedClass = contributesAnnotation.declaringClass
        val moduleAnnotation = contributedClass.find(daggerModuleFqName.asString()).singleOrNull()
        val mergeModulesAnnotation =
          contributedClass.find(mergeModulesFqName.asString()).singleOrNull()

        if (!contributedClass.isInterface() &&
          moduleAnnotation == null &&
          mergeModulesAnnotation == null
        ) {
          throw KspAnvilException(
            message = "${contributedClass.qualifiedName?.asString()} is annotated with " +
              "@${ContributesTo::class.simpleName}, but this class is neither an interface " +
              "nor a Dagger module. Did you forget to add @${Module::class.simpleName}?",
            node = contributedClass,
          )
        }

        moduleAnnotation != null || mergeModulesAnnotation != null
      }
      .onEach { contributesAnnotation ->
        val contributedClass = contributesAnnotation.declaringClass
        if (contributedClass.getVisibility() != Visibility.PUBLIC) {
          throw KspAnvilException(
            message = "${contributedClass.qualifiedName?.asString()} is contributed to the Dagger graph, but the " +
              "module is not public. Only public modules are supported.",
            node = contributedClass,
          )
        }

        // Add to originating declarations as we care about this now
        originatingDeclarations += contributedClass
      }
      // Convert the sequence to a list to avoid iterating it twice. We use the result twice
      // for replaced classes and the final result.
      .toList()

    val (bindingModuleContributesAnnotations, contributesAnnotations) = allContributesAnnotations.partition {
      it.declaringClass.isAnnotationPresent<InternalBindingMarker>()
    }

    val excludedModules = annotations
      .flatMap(KSAnnotation::exclude)
      .asSequence()
      .resolveMergedTypes(resolver)
      .onEach { excludedClass ->
        // Verify that the replaced classes use the same scope.
        val contributesToOurScope = excludedClass
          .findAll(
            contributesToFqName.asString(),
            contributesBindingFqName.asString(),
            contributesMultibindingFqName.asString(),
          )
          .map(KSAnnotation::scope)
          .plus(
            excludedClass
              .find(contributesSubcomponentFqName.asString())
              .map(KSAnnotation::parentScope),
          )
          .any { scope -> scope in scopes }

        if (!contributesToOurScope) {
          val origin = declaration.originClass()
          throw KspAnvilException(
            message = "${origin.qualifiedName?.asString()} with scopes " +
              "${
                scopes.joinToString(
                  prefix = "[",
                  postfix = "]",
                ) { it.resolveKSClassDeclaration()?.qualifiedName?.asString()!! }
              } " +
              "wants to exclude ${excludedClass.qualifiedName?.asString()}, but the excluded class isn't " +
              "contributed to the same scope.",
            node = origin,
          )
        }
      }
      .map(KSClassDeclaration::toClassName)
      .toSet()

    val replacedModules = allContributesAnnotations
      // Ignore replaced modules or bindings specified by excluded modules.
      .filter { contributesAnnotation ->
        contributesAnnotation.declaringClass.toClassName() !in excludedModules
      }
      .flatMap { contributesAnnotation ->
        val contributedClass = contributesAnnotation.declaringClass
        contributesAnnotation.replaces()
          .asSequence()
          .resolveMergedTypes(resolver)
          .onEach { classToReplace ->
            // Verify has @Module annotation. It doesn't make sense for a Dagger module to
            // replace a non-Dagger module.
            if (!classToReplace.isAnnotationPresent<Module>() &&
              !classToReplace.isAnnotationPresent<ContributesBinding>() &&
              !classToReplace.isAnnotationPresent<ContributesMultibinding>()
            ) {
              val origin = contributedClass.originClass()
              throw KspAnvilException(
                message = "${origin.qualifiedName?.asString()} wants to replace " +
                  "${classToReplace.qualifiedName?.asString()}, but the class being " +
                  "replaced is not a Dagger module.",
                node = origin,
              )
            }

            checkSameScope(contributedClass, classToReplace, scopes)
          }
      }
      .map(KSClassDeclaration::toClassName)
      .toSet()

    val bindings = bindingModuleContributesAnnotations
      .mapNotNull { contributedAnnotation ->
        val moduleClass = contributedAnnotation.declaringClass
        val internalBindingMarker =
          moduleClass.find(internalBindingMarkerFqName.asString()).single()

        val bindingFunction = moduleClass.getAllFunctions().single {
          val functionName = it.simpleName.asString()
          functionName.startsWith("bind") || functionName.startsWith("provide")
        }

        val originClass =
          internalBindingMarker.originClass()!!

        if (originClass.toClassName()
            .let { it in excludedModules || it in replacedModules }
        ) {
          return@mapNotNull null
        }
        if (moduleClass.toClassName()
            .let { it in excludedModules || it in replacedModules }
        ) {
          return@mapNotNull null
        }

        val boundType = bindingFunction.returnTypeOrNull()!!.resolveKSClassDeclaration()!!
        val isMultibinding =
          internalBindingMarker.argumentAt("isMultibinding")?.value == true
        val qualifierKey =
          (internalBindingMarker.argumentAt("qualifierKey")?.value as? String?).orEmpty()
        val rank = (
          internalBindingMarker.argumentAt("rank")
            ?.value as? Int?
          )
          ?: ContributesBinding.RANK_NORMAL
        val scope = contributedAnnotation.scope()
        ContributedBinding(
          scope = scope.toClassName(),
          isMultibinding = isMultibinding,
          bindingModule = moduleClass.toClassName(),
          originClass = originClass.toClassName(),
          boundType = boundType,
          qualifierKey = qualifierKey,
          rank = rank,
          replaces = moduleClass.find(contributesToFqName.asString()).single()
            .replaces()
            .map(KSClassDeclaration::toClassName),
        )
      }
      .let(ContributedBindings.Companion::from)

    if (predefinedModules.isNotEmpty()) {
      val intersect = predefinedModules.intersect(excludedModules.toSet())
      if (intersect.isNotEmpty()) {
        throw KspAnvilException(
          message = "${declaration.qualifiedName?.asString()} includes and excludes modules " +
            "at the same time: ${intersect.joinToString(transform = ClassName::canonicalName)}",
          node = declaration,
        )
      }
    }

    // Modules nested in contributed subcomponent classes that we implicit include
    // to bind their factories
    val contributedSubcomponentModules =
      findContributedSubcomponentModules(
        declaration,
        scopes,
        resolver,
      )

    val contributedModules: List<ClassName> = contributesAnnotations
      .asSequence()
      .map { it.declaringClass.toClassName() }
      .plus(
        bindings.bindings.values.flatMap(Map<BindingKey<*>, List<ContributedBinding<*>>>::values)
          .flatten()
          .map(ContributedBinding<*>::bindingModule)
          .map { bindingModule ->
            resolver.getClassDeclarationByName(bindingModule.canonicalName)!!
              .also {
                originatingDeclarations += it
              }
              .toClassName()
          },
      )
      .minus(replacedModules)
      .minus(excludedModules)
      .plus(predefinedModules)
      .plus(contributedSubcomponentModules)
      .distinct()
      .toList()

    val parameterName = if (isModule) {
      "includes"
    } else {
      "modules"
    }

    return AnnotationSpec.builder(daggerAnnotationClassName)
      .addMember(
        "$parameterName = [%L]",
        contributedModules.map { CodeBlock.of("%T::class", it) }.joinToCode(),
      )
      .apply {
        fun copyArrayValue(name: String) {
          val varargArguments = annotations
            .mapNotNull { annotation ->
              @Suppress("UNCHECKED_CAST")
              (annotation.argumentAt(name)?.value as? List<KSType>?)
                ?.map(KSType::toClassName)
            }
            .flatten()
            .ifEmpty { return }

          addMember(
            "$name = [%L]",
            varargArguments.map { CodeBlock.of("%T::class", it) }.joinToCode(),
          )
        }

        if (annotations[0].annotationType.resolve().toClassName() == mergeComponentClassName) {
          copyArrayValue("dependencies")
        }

        if (annotations[0].annotationType.resolve().toClassName() == mergeModulesClassName) {
          copyArrayValue("subcomponents")
        }
      }
      .build()
  }

  private fun contributedInterfaces(
    mergeAnnotations: List<KSAnnotation>,
    originatingDeclarations: MutableList<KSClassDeclaration>,
    resolver: Resolver,
    mergeAnnotatedClass: KSClassDeclaration,
    contributedInterfacesInRound: Map<KSType, List<KSClassDeclaration>>,
  ): List<ClassName> {
    val scopes: Set<KSType> = mergeAnnotations.mapTo(mutableSetOf(), KSAnnotation::scope)
    val contributesAnnotations = mergeAnnotations
      .flatMap { annotation ->
        val scope = annotation.scope()
        classScanner
          .findContributedClasses(
            resolver = resolver,
            annotation = contributesToFqName,
            scope = scope,
          )
          .plus(
            contributorsInRound(resolver, contributedInterfacesInRound, scope, isModule = false),
          )
      }
      .asSequence()
      .filter { clazz ->
        clazz.isInterface() && clazz.findAll(daggerModuleFqName.asString()).singleOrNull() == null
      }
      .flatMap { clazz ->
        clazz
          .findAll(contributesToFqName.asString())
          .filter { it.scope() in scopes }
      }
      .onEach { contributeAnnotation ->
        val contributedClass = contributeAnnotation.declaringClass
        if (contributedClass.getVisibility() != Visibility.PUBLIC) {
          throw KspAnvilException(
            node = contributedClass,
            message = "${contributedClass.qualifiedName?.asString()} is contributed to the Dagger graph, but the " +
              "interface is not public. Only public interfaces are supported.",
          )
        }

        // We're using this class now, so add it to originating declarations
        originatingDeclarations += contributedClass
      }
      // Convert the sequence to a list to avoid iterating it twice. We use the result twice
      // for replaced classes and the final result.
      .toList()

    val replacedClasses = contributesAnnotations
      .flatMap { contributeAnnotation ->
        val contributedClass = contributeAnnotation.declaringClass
        contributedClass
          .atLeastOneAnnotation(
            contributeAnnotation.annotationType.resolve()
              .resolveKSClassDeclaration()!!.qualifiedName!!.asString(),
          )
          .asSequence()
          .flatMap(KSAnnotation::replaces)
          .onEach { classToReplace ->
            // Verify the other class is an interface. It doesn't make sense for a contributed
            // interface to replace a class that is not an interface.
            if (!classToReplace.isInterface()) {
              throw KspAnvilException(
                node = contributedClass,
                message = "${contributedClass.qualifiedName?.asString()} wants to replace " +
                  "${classToReplace.qualifiedName?.asString()}, but the class being " +
                  "replaced is not an interface.",
              )
            }

            val contributesToOurScope = classToReplace
              .findAll(
                contributesToFqName.asString(),
                contributesBindingFqName.asString(),
                contributesMultibindingFqName.asString(),
              )
              .map(KSAnnotation::scope)
              .any { scope -> scope in scopes }

            if (!contributesToOurScope) {
              throw KspAnvilException(
                node = contributedClass,
                message = "${contributedClass.qualifiedName?.asString()} with scopes " +
                  "${
                    scopes.joinToString(
                      prefix = "[",
                      postfix = "]",
                    ) { it.resolveKSClassDeclaration()?.qualifiedName?.asString()!! }
                  } " +
                  "wants to replace ${classToReplace.qualifiedName?.asString()}, but the replaced class isn't " +
                  "contributed to the same scope.",
              )
            }
          }
      }
      .map(KSClassDeclaration::toClassName)
      .toSet()

    val excludedClasses = mergeAnnotations
      .asSequence()
      .flatMap(KSAnnotation::exclude)
      .filter(KSClassDeclaration::isInterface)
      .onEach { excludedClass ->
        // Verify that the replaced classes use the same scope.
        val contributesToOurScope = excludedClass
          .findAll(
            contributesToFqName.asString(),
            contributesBindingFqName.asString(),
            contributesMultibindingFqName.asString(),
          )
          .map(KSAnnotation::scope)
          .plus(
            excludedClass.findAll(contributesSubcomponentFqName.asString())
              .map(KSAnnotation::parentScope),
          )
          .any { scope -> scope in scopes }

        if (!contributesToOurScope) {
          throw KspAnvilException(
            message = "${mergeAnnotatedClass.qualifiedName?.asString()} with scopes " +
              "${
                scopes.joinToString(
                  prefix = "[",
                  postfix = "]",
                ) { it.resolveKSClassDeclaration()?.qualifiedName?.asString()!! }
              } " +
              "wants to exclude ${excludedClass.qualifiedName?.asString()}, but the excluded class isn't " +
              "contributed to the same scope.",
            node = mergeAnnotatedClass,
          )
        }
      }
      .map(KSClassDeclaration::toClassName)
      .toList()

    if (excludedClasses.isNotEmpty()) {
      val intersect: Set<ClassName> = mergeAnnotatedClass
        .superTypesExcludingAny(resolver)
        .mapNotNull { it.resolveKSClassDeclaration()?.toClassName() }
        .toSet()
        // Need to intersect with both merged and origin types to be sure
        .intersect(
          excludedClasses.toSet(),
        )

      if (intersect.isNotEmpty()) {
        throw KspAnvilException(
          node = mergeAnnotatedClass,
          message = "${mergeAnnotatedClass.simpleName.asString()} excludes types that it implements or " +
            "extends. These types cannot be excluded. Look at all the super types to find these " +
            "classes: ${intersect.joinToString(transform = ClassName::canonicalName)}.",
        )
      }
    }

    val supertypesToAdd: List<ClassName> = contributesAnnotations
      .asSequence()
      .map { it.declaringClass.toClassName() }
      .plus(
        scopes.flatMap { scope ->
          contributedInterfacesInRound[scope].orEmpty()
            .map {
              val originClassName = it.toClassName()
              val isMergedType =
                it.isAnnotationPresent<MergeInterfaces>() || it.isAnnotationPresent<MergeComponent>() || it.isAnnotationPresent<MergeSubcomponent>()
              if (isMergedType) {
                originClassName.mergedClassName()
              } else {
                originClassName
              }
            }
        },
      )
      .filter { clazz ->
        clazz !in replacedClasses && clazz !in excludedClasses
      }
      .plus(
        findContributedSubcomponentParentInterfaces(
          clazz = mergeAnnotatedClass,
          scopes = scopes,
          resolver = resolver,
        ).onEach { contributedInterface ->
          // If it's resolvable (may not be yet in this round), add it to originating declarations
          resolver.getClassDeclarationByName(contributedInterface.canonicalName)?.let {
            originatingDeclarations += it
          }
        },
      )
      // Avoids an error for repeated interfaces.
      .distinct()
      .toList()

    return supertypesToAdd
  }

  private fun generateMergedClass(
    resolver: Resolver,
    scope: ClassName,
    mergeAnnotatedClass: KSClassDeclaration,
    originatingDeclarations: MutableList<KSClassDeclaration>,
    generatedComponentClassName: ClassName,
    contributedSubcomponentData: ContributedSubcomponentData?,
    creator: Creator?,
    daggerAnnotation: AnnotationSpec?,
    contributedInterfaces: List<ClassName>?,
    isModule: Boolean,
  ) {
    val mergeAnnotatedClassName = mergeAnnotatedClass.toClassName()
    val generatedComponent = TypeSpec.interfaceBuilder(
      generatedComponentClassName.simpleName,
    )
      .apply {
        // Copy over original annotations
        addAnnotations(
          mergeAnnotatedClass.annotations
            .map(KSAnnotation::toAnnotationSpec)
            .filterNot {
              it.typeName == mergeComponentClassName ||
                it.typeName == mergeSubcomponentClassName ||
                it.typeName == mergeModulesClassName ||
                it.typeName == mergeInterfacesClassName
            }
            .asIterable(),
        )

        // Add our InternalMergedTypeMarker annotation for reference
        addAnnotation(
          AnnotationSpec.builder(InternalMergedTypeMarker::class)
            .addMember("originClass = %T::class", mergeAnnotatedClassName)
            .addMember("scope = %T::class", scope)
            .build(),
        )
        if (!isModule) {
          addSuperinterface(mergeAnnotatedClassName)
        }
        daggerAnnotation?.let(::addAnnotation)

        contributedInterfaces?.forEach(::addSuperinterface)

        val allBindingSpecs = mutableListOf<BindingSpec>()

        // Always generate a binding for the merged type -> root
        allBindingSpecs += BindingSpec(
          impl = generatedComponentClassName,
          boundType = mergeAnnotatedClass.toClassName(),
        )

        // Generate the creator subtype if it exists
        creator?.let { creator ->
          val (creatorSpec, bindingSpecs) = creator.extend(
            mergeAnnotatedClass.toClassName(),
            generatedComponentClassName,
          )
          allBindingSpecs += bindingSpecs
          addType(creatorSpec)

          // Generate a binding module for the component factory
          //
          // @Module
          // interface SubcomponentModule {
          //   @Binds
          //   fun bindComponentFactory(impl: ComponentFactory): UserComponent.ComponentFactory
          // }
          //
          addType(
            generateDaggerBindingModuleForFactory(
              parent = creator.declaration,
              impl = generatedComponentClassName.nestedClass(creatorSpec.name!!),
            ),
          )

          //
          // Generate a ParentComponent for the factory type if this is a subcomponent.
          //
          // interface ParentComponent {
          //   fun createComponentFactory(): ComponentFactory
          // }
          //
          if (mergeAnnotatedClass.isAnnotationPresent<MergeSubcomponent>()) {
            addType(
              generateParentComponentForFactory(
                factoryClassName = generatedComponentClassName.nestedClass(creatorSpec.name!!),
              ),
            )
          }
        }

        // Add the binding module
        addType(
          daggerBindingModuleSpec(
            BINDING_MODULE_SUFFIX,
            allBindingSpecs,
          ),
        )

        // If this is a subcomponent with a contributed interface, generate its parent component
        // here
        if (contributedSubcomponentData != null) {
          val contributor = contributedSubcomponentData.contributor
          if (contributor != null) {
            val parentClass = resolver.getClassDeclarationByName(contributor.canonicalName)!!
            originatingDeclarations += parentClass
            addType(
              generateParentComponentForContributor(
                parent = parentClass,
                mergedSubcomponent = generatedComponentClassName,
              ),
            )
          }
        }
      }
      .addOriginatingKSFile(mergeAnnotatedClass.containingFile!!)
      .apply {
        originatingDeclarations
          .distinctBy { it.fqName }
          .filterNot { classScanner.isExternallyContributed(it) }
          .forEach { it.containingFile?.let(::addOriginatingKSFile) }
      }
      .build()

    val spec = FileSpec.createAnvilSpec(
      generatedComponentClassName.packageName,
      generatedComponent.name!!,
    ) {
      addType(generatedComponent)
      // Generate a shim of what the normal dagger entry point would be
      creator?.daggerFunSpec?.let {
        addType(
          TypeSpec.objectBuilder("Dagger${mergeAnnotatedClass.simpleName.asString().capitalize()}")
            .addFunction(it)
            .build(),
        )
      }
    }

    // Aggregating because we read symbols from the classpath
    spec.writeTo(env.codeGenerator, aggregating = true)
  }

  private inline fun Sequence<KSClassDeclaration>.validate(
    escape: (List<KSClassDeclaration>) -> Nothing,
  ): List<KSClassDeclaration> {
    val (valid, deferred) = partition { annotated ->
      val superTypesHaveError = annotated.superTypes.any { it.resolve().isError }
      if (superTypesHaveError) return@partition false

      val mergeAnnotations = annotated.mergeAnnotations()
      !mergeAnnotations.any { annotation ->
        // If any of the parameters are unresolved, we need to defer this class
        arrayOf("modules", "dependencies", "exclude", "includes").forEach { parameter ->
          @Suppress("UNCHECKED_CAST")
          (annotation.argumentAt(parameter)?.value as? List<KSType>?)?.let { values ->
            if (values.any(KSType::isError)) return@any true
          }
        }
        false
      }

      // Last step - run the validator manually here. It won't run on its own if component
      // processing is enabled.
      KspMergeAnnotationsCheckSymbolProcessor.validate(annotated, mergeAnnotations)
      true
    }
    return if (deferred.isNotEmpty()) {
      escape(deferred)
    } else {
      valid
    }
  }

  private fun findContributedSubcomponentModules(
    declaration: KSClassDeclaration,
    scopes: List<KSType>,
    resolver: Resolver,
  ): Sequence<ClassName> {
    return classScanner
      .findContributedClasses(
        resolver = resolver,
        annotation = contributesSubcomponentFqName,
        scope = null,
      )
      .filter { clazz ->
        clazz.find(contributesSubcomponentFqName.asString())
          .any { it.parentScope().asType(emptyList()) in scopes }
      }
      .flatMap { contributedSubcomponent ->
        //
        // Given the `@ContributeSubcomponent`-annotated class, look up the generated class
        //
        // I.e.
        // @InternalContributedSubcomponentMarker(...)
        // @MergeSubcomponent(scope = Any::class)
        // interface UserComponent_0536E4Be : UserComponent
        //
        val generatedMergeSubcomponentId = contributedSubcomponent.classId
          .generatedAnvilSubcomponentClassId(declaration.classId)

        val generatedMergeSubcomponentDeclaration = resolver.getClassDeclarationByName(
          generatedMergeSubcomponentId.asSingleFqName().toString(),
        )
        if (generatedMergeSubcomponentDeclaration == null) {
          return@flatMap emptyList()
        }

        val contributedSubcomponentData =
          generatedMergeSubcomponentDeclaration.getOrComputeContributedSubcomponentData()

        if (contributedSubcomponentData != null) {
          // This is using our new mechanism
          val modulesToReturn = mutableListOf<ClassName>()
          // Need to include the subcomponent factory binding module
          // com.example.UserComponent_0536E4Be
          val generatedMergeSubcomponentCn = generatedMergeSubcomponentId.asClassName()
          // com.example.MergedUserComponent_0536E4Be
          val mergedSubcomponentCn = generatedMergeSubcomponentCn.mergedClassName()

          // All of these will have a binding module that we should expose to the parent component
          modulesToReturn += generatedMergeSubcomponentCn.nestedClass(BINDING_MODULE_SUFFIX)

          if (contributedSubcomponentData.componentFactory != null) {
            // If there's a factory creator, we need to include the factory binding module
            // com.example.MergedUserComponent_0536E4Be.SubcomponentModule
            val subcomponentCn = mergedSubcomponentCn.nestedClass(SUBCOMPONENT_MODULE)
            modulesToReturn += subcomponentCn
          }
          modulesToReturn
        } else {
          generatedMergeSubcomponentId
            .createNestedClassId(Name.identifier(SUBCOMPONENT_MODULE))
            .let { classId -> listOf(classId.asClassName()) }
        }
      }
  }

  private fun findContributedSubcomponentParentInterfaces(
    clazz: KSClassDeclaration,
    scopes: Set<KSType>,
    resolver: Resolver,
  ): Sequence<ClassName> {
    return classScanner
      .findContributedClasses(
        resolver = resolver,
        annotation = contributesSubcomponentFqName,
        scope = null,
      )
      .filter {
        it.atLeastOneAnnotation(contributesSubcomponentFqName.asString()).single()
          .parentScope().asType(emptyList()) in scopes
      }
      .flatMap { contributedSubcomponent ->
        //
        // Given the `@ContributeSubcomponent`-annotated class, look up the generated class
        //
        // I.e.
        // @InternalContributedSubcomponentMarker(...)
        // @MergeSubcomponent(scope = Any::class)
        // interface UserComponent_0536E4Be : UserComponent
        //

        val generatedMergeSubcomponentId = contributedSubcomponent.classId
          .generatedAnvilSubcomponentClassId(clazz.classId)

        val generatedMergeSubcomponentDeclaration = resolver.getClassDeclarationByName(
          resolver.getKSNameFromString(generatedMergeSubcomponentId.asSingleFqName().toString()),
        )

        val contributedSubcomponentData =
          generatedMergeSubcomponentDeclaration?.getOrComputeContributedSubcomponentData()

        if (contributedSubcomponentData != null) {
          // This is using our new mechanism
          // com.example.UserComponent_0536E4Be
          val generatedMergeSubcomponentCn = generatedMergeSubcomponentId.asClassName()
          val parentComponentClassNames = mutableListOf<ClassName>()
          // com.example.MergedUserComponent_0536E4Be
          val mergedSubcomponentCn = generatedMergeSubcomponentCn.mergedClassName()
          // com.example.MergedUserComponent_0536E4Be.ParentComponent
          val parentComponentCn = mergedSubcomponentCn.nestedClass(PARENT_COMPONENT)
          parentComponentClassNames += parentComponentCn
          if (contributedSubcomponentData.contributor != null) {
            // The parent component is in the contributed subcomponent itself
            parentComponentClassNames += contributedSubcomponentData.contributor
          }
          parentComponentClassNames
        } else {
          // Fall back to old mechanism
          generatedMergeSubcomponentId
            .createNestedClassId(Name.identifier(PARENT_COMPONENT))
            .let { classId -> listOf(classId.asClassName()) }
        }
      }
  }

  private fun KSClassDeclaration.getOrComputeContributedSubcomponentData(): ContributedSubcomponentData? {
    val fqName = this.fqName
    // Can't use getOrPut because it doesn't differentiate null from absent
    if (fqName in contributedSubcomponentDataCache) {
      return contributedSubcomponentDataCache.getValue(fqName)
    } else {
      val value = getKSAnnotationsByType(
        InternalContributedSubcomponentMarker::class,
      )
        .singleOrNull()
        ?.let(ContributedSubcomponentData::fromAnnotation)
      contributedSubcomponentDataCache[fqName] = value
      return value
    }
  }
}

private fun checkSameScope(
  contributedClass: KSClassDeclaration,
  classToReplace: KSClassDeclaration,
  scopes: List<KSType>,
) {
  val contributesToOurScope = classToReplace
    .findAll(
      contributesToFqName.asString(),
      contributesBindingFqName.asString(),
      contributesMultibindingFqName.asString(),
    )
    .map(KSAnnotation::scope)
    .any { scope -> scope in scopes }

  if (!contributesToOurScope) {
    val origin = contributedClass.originClass()
    throw KspAnvilException(
      node = origin,
      message = "${origin.qualifiedName?.asString()} with scopes " +
        "${
          scopes.joinToString(
            prefix = "[",
            postfix = "]",
          ) { it.resolveKSClassDeclaration()?.qualifiedName?.asString()!! }
        } " +
        "wants to replace ${classToReplace.qualifiedName?.asString()}, but the replaced class isn't " +
        "contributed to the same scope.",
    )
  }
}

private fun KSClassDeclaration.originClass(): KSClassDeclaration {
  val originClassValue = find(internalBindingMarkerFqName.asString())
    .singleOrNull()
    ?.argumentAt("originClass")
    ?.value

  val originClass = (originClassValue as? KSType?)?.resolveKSClassDeclaration()
  return originClass ?: this
}

private fun KSAnnotation.originClass(): KSClassDeclaration? {
  val originClassValue = argumentAt("originClass")
    ?.value

  val originClass = (originClassValue as? KSType?)?.resolveKSClassDeclaration()
  return originClass
}

private val KSAnnotation.daggerAnnotationClassName: ClassName
  get() = when (annotationType.resolve().toClassName()) {
    mergeComponentClassName -> daggerComponentClassName
    mergeSubcomponentClassName -> daggerSubcomponentClassName
    mergeModulesClassName -> daggerModuleClassName
    else -> throw NotImplementedError("Don't know how to handle $this.")
  }

private data class CreatorSpecs(
  val creator: TypeSpec,
  val bindings: List<BindingSpec>,
)

private fun Creator.extend(
  originalComponentClassName: ClassName,
  generatedComponentClassName: ClassName,
): CreatorSpecs {
  val name = declaration.simpleName.asString()
  val newClassName = generatedComponentClassName.nestedClass(name)
  val thisType = declaration.asType(emptyList())
  val builder = when (declaration.classKind) {
    ClassKind.INTERFACE -> TypeSpec.interfaceBuilder(name)
      .addSuperinterface(declaration.toClassName())
    ClassKind.CLASS -> TypeSpec.classBuilder(name)
      .superclass(declaration.toClassName())
    ClassKind.ENUM_CLASS,
    ClassKind.ENUM_ENTRY,
    ClassKind.OBJECT,
    ClassKind.ANNOTATION_CLASS,
    -> throw KspAnvilException(
      node = declaration,
      message = "Unsupported class kind: ${declaration.classKind}",
    )
  }

  builder.addAnnotation(daggerAnnotation)

  /*
   // Generate a binding module for the new creator

   @Module
   interface CreatorBindingModule {
     @Binds
     fun bindFactory(impl: MergedAppComponent.Factory): AppComponent.Factory
   }
   */
  val bindingSpecs = listOf(
    BindingSpec(
      impl = newClassName,
      boundType = declaration.toClassName(),
    ),
  )

  /*
   // Generate the new creator

   @Component.Factory
   fun interface Factory : AppComponent.Factory {
     override fun create(): MergedAppComponent
   }
   */
  val creatorSpec = builder
    .addAnnotations(
      declaration.annotations.map(KSAnnotation::toAnnotationSpec)
        .filterNot {
          // Don't copy over our custom creator annotations
          it.typeName == contributesSubcomponentFactoryClassName ||
            it.typeName == mergeComponentFactoryClassName ||
            it.typeName == mergeComponentBuilderClassName ||
            it.typeName == mergeSubcomponentFactoryClassName ||
            it.typeName == mergeSubcomponentBuilderClassName
        }
        .asIterable(),
    )
    .apply {
      for (function in declaration.getDeclaredFunctions()) {
        // Only add the function we need to override and update the type
        if (function.isAbstract) {
          val returnType = function.returnTypeOrNull()
          if (returnType == thisType) {
            // Handles fluent builder functions
            addFunction(
              function.toFunSpec().toBuilder()
                .addModifiers(OVERRIDE, ABSTRACT)
                .returns(newClassName)
                .build(),
            )
          } else if (returnType?.toClassName() == originalComponentClassName) {
            // Handles functions that return the Component class, such as
            // factory create() or builder build()
            addFunction(
              function.toFunSpec().toBuilder()
                .apply {
                  annotations.removeIf { it.typeName.findRawType()?.fqName == contributesSubcomponentFactoryFqName }
                }
                .addModifiers(OVERRIDE, ABSTRACT)
                .returns(generatedComponentClassName)
                .build(),
            )
          }
        }
      }
    }
    .build()

  return CreatorSpecs(creatorSpec, bindingSpecs)
}

private fun Sequence<KSClassDeclaration>.resolveMergedTypes(
  resolver: Resolver,
): Sequence<KSClassDeclaration> {
  return map { it.resolveMergedType(resolver) }
}

private fun KSClassDeclaration.resolveMergedType(resolver: Resolver): KSClassDeclaration {
  val isMergedType = isAnnotationPresent<MergeModules>() ||
    isAnnotationPresent<MergeInterfaces>() ||
    isAnnotationPresent<MergeComponent>() ||
    isAnnotationPresent<MergeSubcomponent>()
  return if (isMergedType) {
    resolver.getClassDeclarationByName(
      toClassName().mergedClassName().canonicalName,
    ) ?: throw KspAnvilException(
      message = "Could not find merged module/interface for ${qualifiedName?.asString()}",
      node = this,
    )
  } else {
    this
  }
}

private fun contributorsInRound(
  resolver: Resolver,
  contributedSymbolsInRound: Map<KSType, List<KSClassDeclaration>>,
  scope: KSType,
  isModule: Boolean,
): List<KSClassDeclaration> {
  return contributedSymbolsInRound[scope].orEmpty()
    .map { contributedSymbol ->
      val mergeAnnotation = contributedSymbol
        .getKSAnnotationsByType(MergeModules::class)
        .plus(
          contributedSymbol
            .getKSAnnotationsByType(MergeInterfaces::class),
        )
        .singleOrNull()
      if (mergeAnnotation != null) {
        // Fake the eventually-generated one for simplicity
        // This is a type in the current round, so we don't have full type information
        // available yet.
        val newName = contributedSymbol.toClassName().mergedClassName()

        // Copy over the ContributesTo annotation to this new fake too, but we need
        // to remap its parent node so that the declaringClass works appropriately
        val contributesToAnnotation = contributedSymbol
          .getKSAnnotationsByType(ContributesTo::class)
          .single()

        val newAnnotations =
          contributedSymbol.annotations - mergeAnnotation - contributesToAnnotation

        // Create a new contributed symbol
        object : KSClassDeclaration by contributedSymbol {
          private val newKSClass = this

          // We also need to fake an `@Module` annotation on to this if this is a module
          private val newModuleAnnotation = if (isModule) {
            object : KSAnnotation by mergeAnnotation {
              override val annotationType: KSTypeReference
                get() = resolver.createKSTypeReferenceFromKSType(
                  resolver.getClassDeclarationByName<Module>()!!.asType(emptyList()),
                )
              override val arguments: List<KSValueArgument> =
                mergeAnnotation.arguments.filterNot {
                  it.name?.asString() in setOf(
                    "scope",
                    "exclude",
                  )
                }
              override val defaultArguments: List<KSValueArgument> =
                mergeAnnotation.defaultArguments.filterNot {
                  it.name?.asString() in setOf(
                    "scope",
                    "exclude",
                  )
                }
              override val parent: KSNode = newKSClass
              override val shortName: KSName = resolver.getKSNameFromString("Module")

              override fun <D, R> accept(visitor: KSVisitor<D, R>, data: D): R {
                throw NotImplementedError()
              }
            }
          } else {
            null
          }

          override val qualifiedName: KSName =
            resolver.getKSNameFromString(newName.canonicalName)
          override val simpleName: KSName =
            resolver.getKSNameFromString(newName.simpleName)

          private val finalNewAnnotations = buildList {
            addAll(newAnnotations)
            newModuleAnnotation?.let(::add)
            add(
              object : KSAnnotation by contributesToAnnotation {
                override val parent: KSNode = newKSClass
              },
            )
          }
          override val annotations: Sequence<KSAnnotation> = finalNewAnnotations.asSequence()
        }
      } else {
        // Standard contributed module, just use it as-is
        contributedSymbol
      }
    }
}

internal sealed interface Creator {
  val mergeAnnotation: KClass<*>
  val daggerAnnotation: KClass<*>
  val declaration: KSClassDeclaration
  val daggerFunSpec: FunSpec?

  data class Factory(
    override val mergeAnnotation: KClass<*>,
    override val daggerAnnotation: KClass<*>,
    override val declaration: KSClassDeclaration,
    override val daggerFunSpec: FunSpec?,
  ) : Creator

  data class Builder(
    override val mergeAnnotation: KClass<*>,
    override val daggerAnnotation: KClass<*>,
    override val declaration: KSClassDeclaration,
    override val daggerFunSpec: FunSpec?,
  ) : Creator

  companion object {
    fun fromDeclaration(
      declaration: KSClassDeclaration,
      mergeAnnotatedClassName: ClassName,
      generatedComponentClassName: ClassName,
    ): Creator? {
      var foundCreator = false
      var isFactory = false
      lateinit var mergeAnnotation: KClass<*>
      lateinit var daggerAnnotation: KClass<*>
      for (annotation in declaration.annotations) {
        when (annotation.fqName) {
          mergeComponentFactoryFqName -> {
            mergeAnnotation = MergeComponent.Factory::class
            daggerAnnotation = Component.Factory::class
            isFactory = true
            foundCreator = true
            break
          }
          mergeSubcomponentFactoryFqName, contributesSubcomponentFactoryFqName -> {
            mergeAnnotation = MergeSubcomponent.Factory::class
            daggerAnnotation = Subcomponent.Factory::class
            isFactory = true
            foundCreator = true
            break
          }
          mergeComponentBuilderFqName -> {
            mergeAnnotation = MergeComponent.Builder::class
            daggerAnnotation = Component.Builder::class
            foundCreator = true
            break
          }
          mergeSubcomponentBuilderFqName -> {
            mergeAnnotation = MergeSubcomponent.Builder::class
            daggerAnnotation = Subcomponent.Builder::class
            foundCreator = true
            break
          }
          else -> continue
        }
      }
      if (!foundCreator) return null

      return if (isFactory) {
        val daggerFunSpec = if (daggerAnnotation == Component.Factory::class) {
          FunSpec.builder("factory")
            .addAnnotation(JvmStatic::class)
            .returns(mergeAnnotatedClassName.nestedClass(declaration.simpleName.asString()))
            .addStatement(
              "return Dagger${
                generatedComponentClassName.simpleName
                  .capitalize()
              }.factory()",
            )
            .build()
        } else {
          null
        }
        Factory(
          declaration = declaration,
          daggerFunSpec = daggerFunSpec,
          mergeAnnotation = mergeAnnotation,
          daggerAnnotation = daggerAnnotation,
        )
      } else {
        // Must be a builder
        val daggerFunSpec = if (daggerAnnotation == Component.Builder::class) {
          FunSpec.builder("builder")
            .addAnnotation(JvmStatic::class)
            .returns(mergeAnnotatedClassName.nestedClass(declaration.simpleName.asString()))
            .addStatement(
              "return Dagger${
                generatedComponentClassName.simpleName
                  .capitalize()
              }.builder()",
            )
            .build()
        } else {
          null
        }
        Builder(
          declaration = declaration,
          daggerFunSpec = daggerFunSpec,
          mergeAnnotation = mergeAnnotation,
          daggerAnnotation = daggerAnnotation,
        )
      }
    }
  }
}

// TODO dedupe logic with ContributesSubcomponentHandler version?
private fun generateDaggerBindingModuleForFactory(
  parent: KSClassDeclaration,
  impl: ClassName,
): TypeSpec {
  // This Dagger module will allow injecting the factory instance.
  return TypeSpec
    .classBuilder(SUBCOMPONENT_MODULE)
    .addModifiers(ABSTRACT)
    .addAnnotation(Module::class)
    .addFunction(
      FunSpec
        .builder("bindSubcomponentFactory")
        .addAnnotation(Binds::class)
        .addModifiers(ABSTRACT)
        .addParameter("factory", impl)
        .returns(parent.toClassName())
        .build(),
    )
    .build()
}

// TODO consolidate with contributessubcomponent handling?
private fun generateParentComponentForContributor(
  parent: KSClassDeclaration,
  mergedSubcomponent: ClassName,
): TypeSpec {
  val functionToOverride = parent.getDeclaredFunctions().single()
    .toFunSpec()
  return TypeSpec
    .interfaceBuilder(PARENT_COMPONENT)
    .addSuperinterface(parent.toClassName())
    .addFunction(
      functionToOverride.toBuilder()
        .addModifiers(ABSTRACT, OVERRIDE)
        .returns(mergedSubcomponent)
        .build(),
    )
    .build()
}

private fun generateParentComponentForFactory(
  factoryClassName: ClassName,
): TypeSpec {
  return TypeSpec
    .interfaceBuilder(PARENT_COMPONENT)
    .addFunction(
      FunSpec
        .builder("createComponentFactory")
        .addModifiers(ABSTRACT)
        .returns(factoryClassName)
        .build(),
    )
    .build()
}

private data class ContributedSubcomponentData(
  val originClass: ClassName,
  val contributor: ClassName?,
  val componentFactory: ClassName?,
) {

  fun resolveCreatorDeclaration(resolver: Resolver): KSClassDeclaration? {
    val creator = contributor ?: componentFactory ?: return null
    return resolver.getClassDeclarationByName(creator.canonicalName)
  }

  companion object {
    fun fromAnnotation(annotation: KSAnnotation): ContributedSubcomponentData {
      val originClass = (annotation.argumentAt("originClass")?.value as KSType).toClassName()
      val contributor = (annotation.argumentAt("contributor")?.value as KSType?)?.toClassName()
        .takeUnless { it == NOTHING }
      val componentFactory =
        (annotation.argumentAt("componentFactory")?.value as KSType?)?.toClassName()
          .takeUnless { it == NOTHING }
      return ContributedSubcomponentData(
        originClass = originClass,
        contributor = contributor,
        componentFactory = componentFactory,
      )
    }
  }
}
