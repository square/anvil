package com.squareup.anvil.compiler

import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.anvil.compiler.codegen.generatedAnvilSubcomponentClassId
import com.squareup.anvil.compiler.codegen.reference.AnnotationReferenceIr
import com.squareup.anvil.compiler.codegen.reference.AnvilCompilationExceptionClassReferenceIr
import com.squareup.anvil.compiler.codegen.reference.ClassReferenceIr
import com.squareup.anvil.compiler.codegen.reference.RealAnvilModuleDescriptor
import com.squareup.anvil.compiler.codegen.reference.find
import com.squareup.anvil.compiler.codegen.reference.findAll
import com.squareup.anvil.compiler.codegen.reference.toClassReference
import com.squareup.anvil.compiler.internal.classIdBestGuess
import com.squareup.anvil.compiler.internal.reference.Visibility.PUBLIC
import dagger.Module
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.backend.jvm.ir.kClassReference
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irVararg
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.classOrFail
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.starProjectedType
import org.jetbrains.kotlin.ir.types.superTypes
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import java.io.File

/**
 * An [IrGenerationExtension] that performs the two types of merging Anvil supports.
 *
 * 1. **Module merging**: This step sources from `@MergeComponent`, `@MergeSubcomponent`, and
 * `@MergeModules` to merge all contributed modules on the classpath to the annotated element.
 *
 * 2. **Interface merging**: This step finds all contributed component interfaces and adds them
 * as super types to Dagger components annotated with `@MergeComponent` or `@MergeSubcomponent`.
 * This also supports arbitrary interface merging on interfaces annotated with `@MergeInterfaces`.
 */
@OptIn(org.jetbrains.kotlin.backend.common.extensions.FirIncompatiblePluginAPI::class)
internal class IrContributionMerger(
  private val classScanner: ClassScanner,
  private val moduleDescriptorFactory: RealAnvilModuleDescriptor.Factory,
  private val trackSourceFiles: Boolean,
  private val irMergesFile: File,
) : IrGenerationExtension {
  // https://youtrack.jetbrains.com/issue/KT-56635
  override val shouldAlsoBeAppliedInKaptStubGenerationMode: Boolean get() = true

  override fun generate(
    moduleFragment: IrModuleFragment,
    pluginContext: IrPluginContext,
  ) {

    val mergedModules = mutableMapOf<FqName, List<FqName>>()
    val mergedInterfaces = mutableMapOf<FqName, List<FqName>>()

    moduleFragment.transform(
      object : IrElementTransformerVoid() {
        override fun visitClass(declaration: IrClass): IrStatement {
          if (declaration.shouldIgnore()) return super.visitClass(declaration)

          val mergeAnnotatedClass = declaration.symbol.toClassReference(pluginContext)

          val mergeComponentAnnotations = mergeAnnotatedClass.annotations
            .findAll(mergeComponentFqName, mergeSubcomponentFqName)

          val mergeModulesAnnotations = mergeAnnotatedClass.annotations
            .findAll(mergeModulesFqName)

          val moduleMergerAnnotations = mergeComponentAnnotations + mergeModulesAnnotations

          if (moduleMergerAnnotations.isNotEmpty()) {

            mergedModules[mergeAnnotatedClass.fqName] = pluginContext.irBuiltIns
              .createIrBuilder(declaration.symbol)
              .addMergedModules(
                annotations = moduleMergerAnnotations,
                moduleFragment = moduleFragment,
                pluginContext = pluginContext,
                declaration = mergeAnnotatedClass,
              )
          }

          val mergeInterfacesAnnotations = mergeAnnotatedClass.annotations
            .findAll(mergeInterfacesFqName)

          val interfaceMergerAnnotations = mergeComponentAnnotations + mergeInterfacesAnnotations

          if (interfaceMergerAnnotations.isNotEmpty()) {
            if (!mergeAnnotatedClass.isInterface) {
              throw AnvilCompilationExceptionClassReferenceIr(
                classReference = mergeAnnotatedClass,
                message = "Dagger components (or classes annotated with @MergeInterfaces)" +
                  " must be interfaces.",
              )
            }

            // Add supertypes to this `mergeAnnotatedClass`
            mergedInterfaces[mergeAnnotatedClass.fqName] = mergeAnnotatedClass
              .addInterfaceSupertypes(
                mergeAnnotations = interfaceMergerAnnotations,
                moduleFragment = moduleFragment,
                pluginContext = pluginContext,
              )
          }

          return super.visitClass(declaration)
        }
      },
      null,
    )

    if (trackSourceFiles) {
      // If any IR changes were made, record them in the merges file.  This is because those changes
      // are not reflected in the .class files, which means they're invisible to Kotlin's incremental logic.
      // The Anvil Gradle plugin will disable incremental logic for this task if this merges file is present.
      if (mergedModules.isNotEmpty() || mergedInterfaces.isNotEmpty()) {
        writeMergesFile(mergedModules, mergedInterfaces)
      } else {
        irMergesFile.delete()
      }
    }
  }

  private fun writeMergesFile(
    mergedModules: MutableMap<FqName, List<FqName>>,
    mergedInterfaces: MutableMap<FqName, List<FqName>>,
  ) {
    val mergedText = buildString {

      mergedModules.entries
        .sortedBy { it.key.asString() }
        .forEach { (key, values) ->
          appendLine("module - $key : $values")
        }

      mergedInterfaces.entries
        .sortedBy { it.key.asString() }
        .forEach { (key, values) ->
          appendLine("interface - $key : $values")
        }
    }

    check(irMergesFile.parentFile.mkdirs() || irMergesFile.parentFile.isDirectory) {
      "Could not generate directory: ${irMergesFile.parentFile}"
    }

    irMergesFile.writeText(mergedText)
  }

  @OptIn(UnsafeDuringIrConstructionAPI::class)
  private fun IrBuilderWithScope.addMergedModules(
    annotations: List<AnnotationReferenceIr>,
    moduleFragment: IrModuleFragment,
    pluginContext: IrPluginContext,
    declaration: ClassReferenceIr,
  ): List<FqName> {
    val daggerAnnotationFqName = annotations[0].daggerAnnotationFqName

    val scopes = annotations.map { it.scope }
    val predefinedModules = annotations.flatMap {
      it.argumentOrNull(it.modulesKeyword)?.value<List<ClassReferenceIr>>().orEmpty()
    }

    val allContributesAnnotations: List<AnnotationReferenceIr> = annotations
      .asSequence()
      .flatMap { annotation ->
        classScanner
          .findContributedClasses(
            pluginContext = pluginContext,
            moduleFragment = moduleFragment,
            annotation = contributesToFqName,
            scope = annotation.scope,
            moduleDescriptorFactory = moduleDescriptorFactory,
          )
      }
      .flatMap { contributedClass ->
        contributedClass.annotations
          .find(annotationName = contributesToFqName)
          .filter { it.scope in scopes }
      }
      .filter { contributesAnnotation ->
        val contributedClass = contributesAnnotation.declaringClass
        val moduleAnnotation = contributedClass.annotations.find(daggerModuleFqName).singleOrNull()
        val mergeModulesAnnotation =
          contributedClass.annotations.find(mergeModulesFqName).singleOrNull()

        if (!contributedClass.isInterface &&
          moduleAnnotation == null &&
          mergeModulesAnnotation == null
        ) {
          throw AnvilCompilationExceptionClassReferenceIr(
            message = "${contributedClass.fqName} is annotated with " +
              "@${ContributesTo::class.simpleName}, but this class is neither an interface " +
              "nor a Dagger module. Did you forget to add @${Module::class.simpleName}?",
            classReference = contributedClass,
          )
        }

        moduleAnnotation != null || mergeModulesAnnotation != null
      }
      .onEach { contributesAnnotation ->
        val contributedClass = contributesAnnotation.declaringClass
        if (contributedClass.visibility != PUBLIC) {
          throw AnvilCompilationExceptionClassReferenceIr(
            message = "${contributedClass.fqName} is contributed to the Dagger graph, but the " +
              "module is not public. Only public modules are supported.",
            classReference = contributedClass,
          )
        }
      }
      // Convert the sequence to a list to avoid iterating it twice. We use the result twice
      // for replaced classes and the final result.
      .toList()

    val (bindingModuleContributesAnnotations, contributesAnnotations) = allContributesAnnotations.partition {
      it.declaringClass.isAnnotatedWith(internalBindingMarkerFqName)
    }

    val excludedModules = annotations
      .flatMap { it.excludedClasses }
      .onEach { excludedClass ->

        // Verify that the replaced classes use the same scope.
        val contributesToOurScope = excludedClass.annotations
          .findAll(contributesToFqName, contributesBindingFqName, contributesMultibindingFqName)
          .map { it.scope }
          .plus(
            excludedClass.annotations
              .find(contributesSubcomponentFqName)
              .map { it.parentScope },
          )
          .any { scope -> scope in scopes }

        if (!contributesToOurScope) {
          val origin = declaration.originClass()
          throw AnvilCompilationExceptionClassReferenceIr(
            message = "${origin.fqName} with scopes " +
              "${scopes.joinToString(prefix = "[", postfix = "]") { it.fqName.asString() }} " +
              "wants to exclude ${excludedClass.fqName}, but the excluded class isn't " +
              "contributed to the same scope.",
            classReference = origin,
          )
        }
      }
      .toSet()

    val replacedModules = allContributesAnnotations
      // Ignore replaced modules or bindings specified by excluded modules.
      .filter { contributesAnnotation ->
        contributesAnnotation.declaringClass !in excludedModules
      }
      .flatMap { contributesAnnotation ->
        val contributedClass = contributesAnnotation.declaringClass
        contributesAnnotation.replacedClasses
          .onEach { classToReplace ->
            // Verify has @Module annotation. It doesn't make sense for a Dagger module to
            // replace a non-Dagger module.
            if (!classToReplace.isAnnotatedWith(daggerModuleFqName) &&
              !classToReplace.isAnnotatedWith(contributesBindingFqName) &&
              !classToReplace.isAnnotatedWith(contributesMultibindingFqName)
            ) {
              val origin = contributedClass.originClass()
              throw AnvilCompilationExceptionClassReferenceIr(
                message = "${origin.fqName} wants to replace " +
                  "${classToReplace.fqName}, but the class being " +
                  "replaced is not a Dagger module.",
                classReference = origin,
              )
            }

            checkSameScope(contributedClass, classToReplace, scopes)
          }
      }
      .toSet()

    val bindings = bindingModuleContributesAnnotations
      .mapNotNull { contributedAnnotation ->
        val moduleClass = contributedAnnotation.declaringClass
        val internalBindingMarker =
          moduleClass.annotations.single { it.fqName == internalBindingMarkerFqName }

        val bindingFunction = moduleClass.clazz.functions.single {
          val functionName = it.owner.name.asString()
          functionName.startsWith("bind") || functionName.startsWith("provide")
        }

        val originClass =
          internalBindingMarker.argumentOrNull("originClass")?.value<ClassReferenceIr>()
            ?: throw AnvilCompilationExceptionClassReferenceIr(
              message = "The origin type of a contributed binding is null.",
              classReference = moduleClass,
            )

        if (originClass in excludedModules || originClass in replacedModules) return@mapNotNull null
        if (moduleClass in excludedModules || moduleClass in replacedModules) return@mapNotNull null

        val boundType = bindingFunction.owner.returnType.classOrFail.toClassReference(
          pluginContext,
        )
        val isMultibinding =
          internalBindingMarker.argumentOrNull("isMultibinding")
            ?.value<Boolean>() == true
        val qualifierKey =
          internalBindingMarker.argumentOrNull("qualifierKey")?.value<String>().orEmpty()
        val rank = internalBindingMarker.argumentOrNull("rank")
          ?.value()
          ?: ContributesBinding.RANK_NORMAL
        val scope = contributedAnnotation.scope
        ContributedBinding(
          scope = scope,
          isMultibinding = isMultibinding,
          bindingModule = moduleClass,
          originClass = originClass,
          boundType = boundType,
          qualifierKey = qualifierKey,
          rank = rank,
        )
      }
      .let { ContributedBindings.from(it) }

    if (predefinedModules.isNotEmpty()) {
      val intersect = predefinedModules.intersect(excludedModules.toSet())
      if (intersect.isNotEmpty()) {
        throw AnvilCompilationExceptionClassReferenceIr(
          message = "${declaration.fqName} includes and excludes modules " +
            "at the same time: ${intersect.joinToString { it.fqName.asString() }}",
          classReference = declaration,
        )
      }
    }

    val contributedSubcomponentModules =
      findContributedSubcomponentModules(
        declaration = declaration,
        scopes = scopes,
        pluginContext = pluginContext,
        moduleFragment = moduleFragment,
      )

    val contributedModules = contributesAnnotations
      .asSequence()
      .map { it.declaringClass }
      .plus(bindings.bindings.values.flatMap { it.values }.flatten().map { it.bindingModule })
      .minus(replacedModules)
      .minus(excludedModules)
      .plus(predefinedModules)
      .plus(contributedSubcomponentModules)
      .distinct()
      .map { it.clazz.owner }

    // Since we are modifying the state of the code here, this does not need to be reflected in
    // the associated [ClassReferenceIr] which is more of an initial snapshot.
    declaration.clazz.owner.annotations += createDaggerAnnotation(
      pluginContext = pluginContext,
      daggerAnnotationFqName = daggerAnnotationFqName,
      contributedModules = contributedModules,
      annotations = annotations,
    )

    return contributedModules.mapTo(mutableListOf()) { it.fqName }
  }

  private fun IrBuilderWithScope.createDaggerAnnotation(
    pluginContext: IrPluginContext,
    daggerAnnotationFqName: FqName,
    contributedModules: Sequence<IrClass>,
    annotations: List<AnnotationReferenceIr>,
  ): IrConstructorCall {
    @OptIn(UnsafeDuringIrConstructionAPI::class)
    return irCallConstructor(
      callee = pluginContext
        .referenceConstructors(daggerAnnotationFqName.classIdBestGuess())
        .single { it.owner.isPrimary },
      typeArguments = emptyList(),
    )
      .apply {
        putValueArgument(
          index = 0,
          valueArgument = irVararg(
            elementType = pluginContext.irBuiltIns.kClassClass.starProjectedType,
            values = contributedModules
              .map {
                kClassReference(
                  classType = it.defaultType,
                )
              }
              .toList(),
          ),
        )

        fun copyArrayValue(name: String) {
          val varargArguments = annotations
            .mapNotNull { it.argumentOrNull(name)?.argumentExpression as? IrVararg }
            .ifEmpty { return }

          putValueArgument(
            index = 1,
            valueArgument = irVararg(
              elementType = varargArguments[0].varargElementType,
              // These are always IrExpression instances too
              values = varargArguments.flatMap { it.elements }
                .filterIsInstance<IrExpression>(),
            ),
          )
        }

        if (annotations[0].fqName == mergeComponentFqName) {
          copyArrayValue("dependencies")
        }

        if (annotations[0].fqName == mergeModulesFqName) {
          copyArrayValue("subcomponents")
        }
      }
  }

  private fun checkSameScope(
    contributedClass: ClassReferenceIr,
    classToReplace: ClassReferenceIr,
    scopes: List<ClassReferenceIr>,
  ) {
    val contributesToOurScope = classToReplace.annotations
      .findAll(contributesToFqName, contributesBindingFqName, contributesMultibindingFqName)
      .map { it.scope }
      .any { scope -> scope in scopes }

    if (!contributesToOurScope) {
      val origin = contributedClass.originClass()
      throw AnvilCompilationExceptionClassReferenceIr(
        classReference = origin,
        message = "${origin.fqName} with scopes " +
          "${scopes.joinToString(prefix = "[", postfix = "]") { it.fqName.asString() }} " +
          "wants to replace ${classToReplace.fqName}, but the replaced class isn't " +
          "contributed to the same scope.",
      )
    }
  }

  private fun ClassReferenceIr.originClass(): ClassReferenceIr {
    val originClass = annotations
      .find(internalBindingMarkerFqName)
      .singleOrNull()
      ?.argumentOrNull("originClass")
      ?.value<ClassReferenceIr>()
    return originClass ?: this
  }

  private fun findContributedSubcomponentModules(
    declaration: ClassReferenceIr,
    scopes: List<ClassReferenceIr>,
    pluginContext: IrPluginContext,
    moduleFragment: IrModuleFragment,
  ): Sequence<ClassReferenceIr> {
    return classScanner
      .findContributedClasses(
        pluginContext = pluginContext,
        moduleFragment = moduleFragment,
        annotation = contributesSubcomponentFqName,
        scope = null,
        moduleDescriptorFactory = moduleDescriptorFactory,
      )
      .filter { clazz ->
        clazz.annotations.find(contributesSubcomponentFqName).any { it.parentScope in scopes }
      }
      .mapNotNull { contributedSubcomponent ->
        contributedSubcomponent.classId
          .generatedAnvilSubcomponentClassId(declaration.classId)
          .createNestedClassId(Name.identifier(SUBCOMPONENT_MODULE))
          .referenceClassOrNull(pluginContext)
      }
  }

  private fun ClassReferenceIr.addInterfaceSupertypes(
    mergeAnnotations: List<AnnotationReferenceIr>,
    moduleFragment: IrModuleFragment,
    pluginContext: IrPluginContext,
  ): List<FqName> {
    val scopes = mergeAnnotations.map { it.scope }
    val contributesAnnotations = mergeAnnotations
      .flatMap { annotation ->
        classScanner
          .findContributedClasses(
            pluginContext = pluginContext,
            moduleFragment = moduleFragment,
            annotation = contributesToFqName,
            scope = annotation.scope,
            moduleDescriptorFactory = moduleDescriptorFactory,
          )
      }
      .asSequence()
      .filter { clazz ->
        clazz.isInterface && clazz.annotations.find(daggerModuleFqName).singleOrNull() == null
      }
      .flatMap { clazz ->
        clazz.annotations
          .find(annotationName = contributesToFqName)
          .filter { it.scope in scopes }
      }
      .onEach { contributeAnnotation ->
        val contributedClass = contributeAnnotation.declaringClass
        if (contributedClass.visibility != PUBLIC) {
          throw AnvilCompilationExceptionClassReferenceIr(
            classReference = contributedClass,
            message = "${contributedClass.fqName} is contributed to the Dagger graph, but the " +
              "interface is not public. Only public interfaces are supported.",
          )
        }
      }
      // Convert the sequence to a list to avoid iterating it twice. We use the result twice
      // for replaced classes and the final result.
      .toList()

    val replacedClasses = contributesAnnotations
      .flatMap { contributeAnnotation ->
        val contributedClass = contributeAnnotation.declaringClass
        contributedClass
          .atLeastOneAnnotation(contributeAnnotation.fqName)
          .asSequence()
          .flatMap { it.replacedClasses }
          .onEach { classToReplace ->
            // Verify the other class is an interface. It doesn't make sense for a contributed
            // interface to replace a class that is not an interface.
            if (!classToReplace.isInterface) {
              throw AnvilCompilationExceptionClassReferenceIr(
                classReference = contributedClass,
                message = "${contributedClass.fqName} wants to replace " +
                  "${classToReplace.fqName}, but the class being " +
                  "replaced is not an interface.",
              )
            }

            val contributesToOurScope = classToReplace.annotations
              .findAll(
                contributesToFqName,
                contributesBindingFqName,
                contributesMultibindingFqName,
              )
              .map { it.scope }
              .any { scope -> scope in scopes }

            if (!contributesToOurScope) {
              throw AnvilCompilationExceptionClassReferenceIr(
                classReference = contributedClass,
                message = "${contributedClass.fqName} with scopes " +
                  "${scopes.joinToString(prefix = "[", postfix = "]") { it.fqName.asString() }} " +
                  "wants to replace ${classToReplace.fqName}, but the replaced class isn't " +
                  "contributed to the same scope.",
              )
            }
          }
      }
      .toSet()

    val excludedClasses = mergeAnnotations
      .asSequence()
      .flatMap { it.excludedClasses }
      .filter { it.isInterface }
      .onEach { excludedClass ->
        // Verify that the replaced classes use the same scope.
        val contributesToOurScope = excludedClass.annotations
          .findAll(contributesToFqName, contributesBindingFqName, contributesMultibindingFqName)
          .map { it.scope }
          .plus(
            excludedClass.annotations.find(contributesSubcomponentFqName).map { it.parentScope },
          )
          .any { scope -> scope in scopes }

        if (!contributesToOurScope) {
          throw AnvilCompilationExceptionClassReferenceIr(
            message = "$fqName with scopes " +
              "${scopes.joinToString(prefix = "[", postfix = "]") { it.fqName.asString() }} " +
              "wants to exclude ${excludedClass.fqName}, but the excluded class isn't " +
              "contributed to the same scope.",
            classReference = this,
          )
        }
      }
      .toList()

    val supertypes = clazz.superTypes()
    if (excludedClasses.isNotEmpty()) {
      val intersect = supertypes
        .map { it.classOrFail.toClassReference(pluginContext) }
        .flatMap {
          it.allSuperTypeClassReferences(pluginContext, includeSelf = true)
        }
        .intersect(excludedClasses.toSet())

      if (intersect.isNotEmpty()) {
        throw AnvilCompilationExceptionClassReferenceIr(
          classReference = this@addInterfaceSupertypes,
          message = "$fqName excludes types that it implements or " +
            "extends. These types cannot be excluded. Look at all the super types to find these " +
            "classes: ${intersect.joinToString { it.fqName.asString() }}.",
        )
      }
    }

    val toAdd = contributesAnnotations
      .asSequence()
      .map { it.declaringClass }
      .filter { clazz ->
        clazz !in replacedClasses && clazz !in excludedClasses
      }
      .plus(
        findContributedSubcomponentParentInterfaces(
          clazz = this@addInterfaceSupertypes,
          scopes = scopes,
          pluginContext = pluginContext,
          moduleFragment = moduleFragment,
        ),
      )
      // Avoids an error for repeated interfaces.
      .distinct()
      .map { it.clazz.typeWith() }
      .toList()

    // Since we are modifying the state of the code here, this does not need to be reflected in
    // the associated [ClassReferenceIr] which is more of an initial snapshot.
    @OptIn(UnsafeDuringIrConstructionAPI::class)
    clazz.owner.superTypes += toAdd

    // Return the list of added supertypes
    return toAdd.map { it.classFqName!! }.sortedBy { it.asString() }
  }

  private fun findContributedSubcomponentParentInterfaces(
    clazz: ClassReferenceIr,
    scopes: Collection<ClassReferenceIr>,
    pluginContext: IrPluginContext,
    moduleFragment: IrModuleFragment,
  ): Sequence<ClassReferenceIr> {
    return classScanner
      .findContributedClasses(
        pluginContext = pluginContext,
        moduleFragment = moduleFragment,
        annotation = contributesSubcomponentFqName,
        scope = null,
        moduleDescriptorFactory = moduleDescriptorFactory,
      )
      .filter {
        it.atLeastOneAnnotation(contributesSubcomponentFqName).single()
          .parentScope in scopes
      }
      .mapNotNull { contributedSubcomponent ->
        contributedSubcomponent.classId
          .generatedAnvilSubcomponentClassId(clazz.classId)
          .createNestedClassId(Name.identifier(PARENT_COMPONENT))
          .referenceClassOrNull(pluginContext)
      }
  }
}

private val AnnotationReferenceIr.daggerAnnotationFqName: FqName
  get() = when (fqName) {
    mergeComponentFqName -> daggerComponentFqName
    mergeSubcomponentFqName -> daggerSubcomponentFqName
    mergeModulesFqName -> daggerModuleFqName
    else -> throw NotImplementedError("Don't know how to handle $this.")
  }

private val AnnotationReferenceIr.modulesKeyword: String
  get() = when (fqName) {
    mergeComponentFqName, mergeSubcomponentFqName -> "modules"
    mergeModulesFqName -> "includes"
    else -> throw NotImplementedError("Don't know how to handle $this.")
  }

private fun ClassReferenceIr.allSuperTypeClassReferences(
  context: IrPluginContext,
  includeSelf: Boolean = false,
): Sequence<ClassReferenceIr> {
  return generateSequence(listOf(this)) { superTypes ->
    superTypes
      .flatMap { classRef ->
        classRef.clazz.superTypes()
          .mapNotNull { it.classOrNull?.toClassReference(context) }
      }
      .takeIf { it.isNotEmpty() }
  }
    .drop(if (includeSelf) 0 else 1)
    .flatten()
    .distinct()
}

private fun ClassReferenceIr.atLeastOneAnnotation(
  annotationName: FqName,
  scopeName: FqName? = null,
): List<AnnotationReferenceIr> {
  return annotations.find(annotationName = annotationName, scopeName = scopeName)
    .ifEmpty {
      throw AnvilCompilationExceptionClassReferenceIr(
        classReference = this,
        message = "Class $fqName is not annotated with $annotationName" +
          "${if (scopeName == null) "" else " with scope $scopeName"}.",
      )
    }
}
