package com.squareup.anvil.compiler

import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.anvil.compiler.codegen.atLeastOneAnnotation
import com.squareup.anvil.compiler.codegen.find
import com.squareup.anvil.compiler.codegen.findAll
import com.squareup.anvil.compiler.codegen.generatedAnvilSubcomponentClassId
import com.squareup.anvil.compiler.codegen.parentScope
import com.squareup.anvil.compiler.codegen.reference.RealAnvilModuleDescriptor
import com.squareup.anvil.compiler.internal.classDescriptor
import com.squareup.anvil.compiler.internal.reference.AnnotationReference
import com.squareup.anvil.compiler.internal.reference.AnvilCompilationExceptionClassReference
import com.squareup.anvil.compiler.internal.reference.AnvilModuleDescriptor
import com.squareup.anvil.compiler.internal.reference.ClassReference
import com.squareup.anvil.compiler.internal.reference.Visibility
import com.squareup.anvil.compiler.internal.reference.Visibility.PUBLIC
import com.squareup.anvil.compiler.internal.reference.allSuperTypeClassReferences
import com.squareup.anvil.compiler.internal.reference.argumentAt
import com.squareup.anvil.compiler.internal.reference.toClassReference
import com.squareup.anvil.compiler.internal.reference.toClassReferenceOrNull
import dagger.Module
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.descriptorUtil.classId
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.resolve.extensions.SyntheticResolveExtension
import org.jetbrains.kotlin.types.KotlinType

/**
 * Finds all contributed component interfaces and adds them as super types to Dagger components
 * annotated with `@MergeComponent` or `@MergeSubcomponent`.
 */
internal class SyntheticContributionMerger(
  private val classScanner: ClassScanner,
  private val moduleDescriptorFactory: RealAnvilModuleDescriptor.Factory,
) : SyntheticResolveExtension {

  override fun generateSyntheticProperties(
    thisDescriptor: ClassDescriptor,
    name: Name,
    bindingContext: BindingContext,
    fromSupertypes: ArrayList<PropertyDescriptor>,
    result: MutableSet<PropertyDescriptor>,
  ) {
    if (thisDescriptor.shouldIgnore()) return

    val module = moduleDescriptorFactory.create(thisDescriptor.module)
    val mergeAnnotatedClass = thisDescriptor.toClassReference(module)

    val mergeComponentAnnotations = mergeAnnotatedClass.annotations
      .findAll(mergeComponentFqName, mergeSubcomponentFqName)

    val mergeModulesAnnotations = mergeAnnotatedClass.annotations
      .findAll(mergeModulesFqName)

    val moduleMergerAnnotations = mergeComponentAnnotations + mergeModulesAnnotations

    if (moduleMergerAnnotations.isEmpty()) {
      super.generateSyntheticProperties(
        thisDescriptor = thisDescriptor,
        name = name,
        bindingContext = bindingContext,
        fromSupertypes = fromSupertypes,
        result = result,
      )
      return
    }

    classScanner.findContributedClasses(
      module = thisDescriptor.module,
      annotation = contributesToFqName,
      scope = null,
    )
      .let {
      }
    super.generateSyntheticProperties(thisDescriptor, name, bindingContext, fromSupertypes, result)
  }

  private val AnnotationReference.daggerAnnotationFqName: FqName
    get() = when (fqName) {
      mergeComponentFqName -> daggerComponentFqName
      mergeSubcomponentFqName -> daggerSubcomponentFqName
      mergeModulesFqName -> daggerModuleFqName
      else -> throw NotImplementedError("Don't know how to handle $this.")
    }

  private val AnnotationReference.modulesKeyword: String
    get() = when (fqName) {
      mergeComponentFqName, mergeSubcomponentFqName -> "modules"
      mergeModulesFqName -> "includes"
      else -> throw NotImplementedError("Don't know how to handle $this.")
    }

  @Deprecated("Use the function", ReplaceWith("exclude()"))
  val AnnotationReference.excludedClasses: List<ClassReference> get() = exclude()

  @Deprecated("Use the function", ReplaceWith("replaces()"))
  val AnnotationReference.replacedClasses: List<ClassReference> get() = replaces()

  private fun addMergedModules(
    annotations: List<AnnotationReference>,
    module: AnvilModuleDescriptor,
    declaration: ClassReference.Descriptor,
  ): List<FqName> {
    val daggerAnnotationFqName = annotations[0].daggerAnnotationFqName

    val scopes = annotations.map { it.scope() }
    val predefinedModules = annotations.flatMap { annotation ->
      annotation.arguments
        .singleOrNull { arg -> arg.name == annotation.modulesKeyword }
        ?.value<List<ClassReference>>().orEmpty()
    }

    val allContributesAnnotations: List<AnnotationReference> = annotations
      .asSequence()
      .flatMap { annotation ->
        classScanner
          .findContributedClasses(
            module = module,
            annotation = contributesToFqName,
            scope = annotation.scope().fqName,
          )
      }
      .flatMap { contributedClass ->
        contributedClass.annotations
          .find(annotationName = contributesToFqName)
          .filter { it.scope() in scopes }
      }
      .filter { contributesAnnotation ->
        val contributedClass = contributesAnnotation.declaringClass()
        val moduleAnnotation = contributedClass.annotations.find(daggerModuleFqName).singleOrNull()
        val mergeModulesAnnotation =
          contributedClass.annotations.find(mergeModulesFqName).singleOrNull()

        if (!contributedClass.isInterface() &&
          moduleAnnotation == null &&
          mergeModulesAnnotation == null
        ) {
          throw AnvilCompilationExceptionClassReference(
            message = "${contributedClass.fqName} is annotated with " +
              "@${ContributesTo::class.simpleName}, but this class is neither an interface " +
              "nor a Dagger module. Did you forget to add @${Module::class.simpleName}?",
            classReference = contributedClass,
          )
        }

        moduleAnnotation != null || mergeModulesAnnotation != null
      }
      .onEach { contributesAnnotation ->
        val contributedClass = contributesAnnotation.declaringClass()
        if (contributedClass.visibility() != PUBLIC) {
          throw AnvilCompilationExceptionClassReference(
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
      it.declaringClass().isAnnotatedWith(internalBindingMarkerFqName)
    }

    val excludedModules = annotations
      .flatMap { it.exclude() }
      .onEach { excludedClass ->

        // Verify that the replaced classes use the same scope.
        val contributesToOurScope = excludedClass.annotations
          .findAll(contributesToFqName, contributesBindingFqName, contributesMultibindingFqName)
          .map { it.scope() }
          .plus(
            excludedClass.annotations
              .find(contributesSubcomponentFqName)
              .map { it.parentScope() },
          )
          .any { scope -> scope in scopes }

        if (!contributesToOurScope) {
          val origin = declaration.originClass()
          throw AnvilCompilationExceptionClassReference(
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
        contributesAnnotation.declaringClass() !in excludedModules
      }
      .flatMap { contributesAnnotation ->
        val contributedClass = contributesAnnotation.declaringClass()
        contributesAnnotation.replaces()
          .onEach { classToReplace ->
            // Verify has @Module annotation. It doesn't make sense for a Dagger module to
            // replace a non-Dagger module.
            if (!classToReplace.isAnnotatedWith(daggerModuleFqName) &&
              !classToReplace.isAnnotatedWith(contributesBindingFqName) &&
              !classToReplace.isAnnotatedWith(contributesMultibindingFqName)
            ) {
              val origin = contributedClass.originClass()
              throw AnvilCompilationExceptionClassReference(
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
        val moduleClass = contributedAnnotation.declaringClass()
        val internalBindingMarker = moduleClass.annotations
          .single { it.fqName == internalBindingMarkerFqName }

        val bindingFunction = moduleClass.declaredMemberFunctions.single {
          val functionName = it.name
          functionName.startsWith("bind") || functionName.startsWith("provide")
        }

        val originClass = internalBindingMarker
          .argumentAt(name = "originClass", index = 1)
          ?.value<ClassReference>()
          ?: throw AnvilCompilationExceptionClassReference(
            message = "The origin type of a contributed binding is null.",
            classReference = moduleClass,
          )

        if (originClass in excludedModules || originClass in replacedModules) return@mapNotNull null
        if (moduleClass in excludedModules || moduleClass in replacedModules) return@mapNotNull null

        val boundType = bindingFunction.returnType().asClassReference()

        val isMultibinding = internalBindingMarker.argumentAt(name = "isMultibinding", index = 2)
          ?.value<Boolean>() == true

        val qualifierKey = internalBindingMarker.argumentAt(name = "qualifierKey", index = 4)
          ?.value<String>().orEmpty()
        val rank = internalBindingMarker.argumentAt(name = "rank", index = 3)
          ?.value()
          ?: ContributesBinding.RANK_NORMAL

        val scope = contributedAnnotation.scope()
        ContributedBinding2(
          scope = scope,
          isMultibinding = isMultibinding,
          bindingModule = moduleClass,
          originClass = originClass,
          boundType = boundType,
          qualifierKey = qualifierKey,
          rank = rank,
        )
      }
      .let { ContributedBindings2.from(it) }

    if (predefinedModules.isNotEmpty()) {
      val intersect = predefinedModules.intersect(excludedModules.toSet())
      if (intersect.isNotEmpty()) {
        throw AnvilCompilationExceptionClassReference(
          message = "${declaration.fqName} includes and excludes modules " +
            "at the same time: ${intersect.joinToString { it.fqName.asString() }}",
          classReference = declaration,
        )
      }
    }

    val contributedSubcomponentModules = findContributedSubcomponentModules(
      declaration = declaration,
      scopes = scopes,
      module = module,
    )

    return contributesAnnotations
      .asSequence()
      .map { it.declaringClass() }
      .plus(bindings.bindings.values.flatMap { it.values }.flatten().map { it.bindingModule })
      .minus(replacedModules)
      .minus(excludedModules)
      .plus(predefinedModules)
      .plus(contributedSubcomponentModules)
      .distinct().mapTo(mutableListOf()) { it.fqName }
  }

  private fun findContributedSubcomponentModules(
    declaration: ClassReference,
    scopes: List<ClassReference>,
    module: AnvilModuleDescriptor,
  ): Sequence<ClassReference> {
    return classScanner.findContributedClasses(
      module,
      annotation = contributesSubcomponentFqName,
      scope = null,
    )
      .filter { clazz ->
        clazz.annotations.find(contributesSubcomponentFqName).any { it.parentScope() in scopes }
      }
      .mapNotNull { contributedSubcomponent ->
        contributedSubcomponent.classId
          .generatedAnvilSubcomponentClassId(declaration.classId)
          .createNestedClassId(Name.identifier(SUBCOMPONENT_MODULE))
          .classReferenceOrNull(module)
      }
  }

  private fun ClassReference.originClass(): ClassReference {
    val originClass = annotations
      .find(internalBindingMarkerFqName)
      .singleOrNull()
      ?.argumentAt("originClass", index = 1)
      ?.value<ClassReference>()
    return originClass ?: this
  }

  private fun checkSameScope(
    contributedClass: ClassReference,
    classToReplace: ClassReference,
    scopes: List<ClassReference>,
  ) {
    val contributesToOurScope = classToReplace.annotations
      .findAll(contributesToFqName, contributesBindingFqName, contributesMultibindingFqName)
      .map { it.scope() }
      .any { scope -> scope in scopes }

    if (!contributesToOurScope) {
      val origin = contributedClass.originClass()
      throw AnvilCompilationExceptionClassReference(
        classReference = origin,
        message = "${origin.fqName} with scopes " +
          "${scopes.joinToString(prefix = "[", postfix = "]") { it.fqName.asString() }} " +
          "wants to replace ${classToReplace.fqName}, but the replaced class isn't " +
          "contributed to the same scope.",
      )
    }
  }

  // If we're evaluating an anonymous inner class, it cannot merge anything and will cause
  // a failure if we try to resolve its [ClassId]
  private fun ClassDescriptor.shouldIgnore(): Boolean {
    return classId == null || DescriptorUtils.isAnonymousObject(this)
  }

  override fun addSyntheticSupertypes(
    thisDescriptor: ClassDescriptor,
    supertypes: MutableList<KotlinType>,
  ) {
    if (thisDescriptor.shouldIgnore()) return

    val module = moduleDescriptorFactory.create(thisDescriptor.module)
    val mergeAnnotatedClass = thisDescriptor.toClassReference(module)

    val mergeAnnotations = mergeAnnotatedClass.annotations
      .findAll(mergeComponentFqName, mergeSubcomponentFqName, mergeInterfacesFqName)
      .ifEmpty { return }

    if (!mergeAnnotatedClass.isInterface()) {
      throw AnvilCompilationExceptionClassReference(
        classReference = mergeAnnotatedClass,
        message = "Dagger components must be interfaces.",
      )
    }

    val scopes = mergeAnnotations.map {
      try {
        it.scope()
      } catch (e: AssertionError) {
        // In some scenarios this error is thrown. Throw a new exception with a better explanation.
        // Caused by: java.lang.AssertionError: Recursion detected in a lazy value under
        // LockBasedStorageManager@420989af (TopDownAnalyzer for JVM)
        throw AnvilCompilationExceptionClassReference(
          classReference = mergeAnnotatedClass,
          message = "It seems like you tried to contribute an inner class to its outer class. " +
            "This is not supported and results in a compiler error.",
          cause = e,
        )
      }
    }

    val contributesAnnotations = mergeAnnotations
      .flatMap { annotation ->
        classScanner
          .findContributedClasses(
            module = module,
            annotation = contributesToFqName,
            scope = annotation.scope().fqName,
          )
      }
      .filter { clazz ->
        clazz.isInterface() && clazz.annotations.find(daggerModuleFqName).singleOrNull() == null
      }
      .flatMap { clazz ->
        clazz.annotations
          .find(annotationName = contributesToFqName)
          .filter { it.scope() in scopes }
      }
      .onEach { contributeAnnotation ->
        val contributedClass = contributeAnnotation.declaringClass()
        if (contributedClass.visibility() != Visibility.PUBLIC) {
          throw AnvilCompilationExceptionClassReference(
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
        val contributedClass = contributeAnnotation.declaringClass()
        contributedClass
          .atLeastOneAnnotation(contributeAnnotation.fqName)
          .flatMap { it.replaces() }
          .onEach { classToReplace ->
            // Verify the other class is an interface. It doesn't make sense for a contributed
            // interface to replace a class that is not an interface.
            if (!classToReplace.isInterface()) {
              throw AnvilCompilationExceptionClassReference(
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
              .map { it.scope() }
              .any { scope -> scope in scopes }

            if (!contributesToOurScope) {
              throw AnvilCompilationExceptionClassReference(
                classReference = contributedClass,
                message = "${contributedClass.fqName} with scopes " +
                  "${
                    scopes.joinToString(
                      prefix = "[",
                      postfix = "]",
                    ) { it.fqName.asString() }
                  } " +
                  "wants to replace ${classToReplace.fqName}, but the replaced class isn't " +
                  "contributed to the same scope.",
              )
            }
          }
      }
      .toSet()

    val excludedClasses = mergeAnnotations
      .flatMap { it.exclude() }
      .filter { it.isInterface() }
      .onEach { excludedClass ->
        // Verify that the replaced classes use the same scope.
        val contributesToOurScope = excludedClass.annotations
          .findAll(contributesToFqName, contributesBindingFqName, contributesMultibindingFqName)
          .map { it.scope() }
          .plus(
            excludedClass.annotations.find(contributesSubcomponentFqName).map { it.parentScope() },
          )
          .any { scope -> scope in scopes }

        if (!contributesToOurScope) {
          throw AnvilCompilationExceptionClassReference(
            message = "${mergeAnnotatedClass.fqName} with scopes " +
              "${scopes.joinToString(prefix = "[", postfix = "]") { it.fqName.asString() }} " +
              "wants to exclude ${excludedClass.fqName}, but the excluded class isn't " +
              "contributed to the same scope.",
            classReference = mergeAnnotatedClass,
          )
        }
      }

    if (excludedClasses.isNotEmpty()) {
      val intersect = supertypes
        .map { it.classDescriptor().toClassReference(module) }
        .flatMap { it.allSuperTypeClassReferences(includeSelf = true) }
        .intersect(excludedClasses.toSet())

      if (intersect.isNotEmpty()) {
        throw AnvilCompilationExceptionClassReference(
          classReference = mergeAnnotatedClass,
          message = "${mergeAnnotatedClass.fqName} excludes types that it implements or " +
            "extends. These types cannot be excluded. Look at all the super types to find these " +
            "classes: ${intersect.joinToString { it.fqName.asString() }}.",
        )
      }
    }

    @Suppress("ConvertCallChainIntoSequence")
    supertypes += contributesAnnotations
      .map { it.declaringClass() }
      .filter { clazz ->
        clazz !in replacedClasses && clazz !in excludedClasses
      }
      .plus(findContributedSubcomponentParentInterfaces(mergeAnnotatedClass, scopes, module))
      // Avoids an error for repeated interfaces.
      .distinct()
      .map { it.clazz.defaultType }
  }

  private fun findContributedSubcomponentParentInterfaces(
    clazz: ClassReference,
    scopes: Collection<ClassReference>,
    module: ModuleDescriptor,
  ): Sequence<ClassReference.Descriptor> {
    return classScanner
      .findContributedClasses(
        module = module,
        annotation = contributesSubcomponentFqName,
        scope = null,
      )
      .filter {
        it.atLeastOneAnnotation(contributesSubcomponentFqName).single().parentScope() in scopes
      }
      .mapNotNull { contributedSubcomponent ->
        contributedSubcomponent.classId
          .generatedAnvilSubcomponentClassId(clazz.classId)
          .createNestedClassId(Name.identifier(PARENT_COMPONENT))
          .classReferenceOrNull(module)
      }
  }

  @Suppress("UNCHECKED_CAST")
  private fun <T : ClassReference> ClassId.classReferenceOrNull(
    module: ModuleDescriptor,
  ): T? = asSingleFqName().toClassReferenceOrNull(module) as T?
}
