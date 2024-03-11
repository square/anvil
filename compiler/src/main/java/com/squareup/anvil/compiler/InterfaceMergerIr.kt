package com.squareup.anvil.compiler

import com.squareup.anvil.compiler.codegen.generatedAnvilSubcomponent
import com.squareup.anvil.compiler.codegen.reference.AnnotationReferenceIr
import com.squareup.anvil.compiler.codegen.reference.AnvilCompilationExceptionClassReferenceIr
import com.squareup.anvil.compiler.codegen.reference.ClassReferenceIr
import com.squareup.anvil.compiler.codegen.reference.RealAnvilModuleDescriptor
import com.squareup.anvil.compiler.codegen.reference.find
import com.squareup.anvil.compiler.codegen.reference.findAll
import com.squareup.anvil.compiler.codegen.reference.toClassReference
import com.squareup.anvil.compiler.internal.reference.ClassReference
import com.squareup.anvil.compiler.internal.reference.Visibility.PUBLIC
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.types.classOrFail
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.superTypes
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtClassOrObject

/**
 * Finds all contributed component interfaces and adds them as super types to Dagger components
 * annotated with `@MergeComponent` or `@MergeSubcomponent`.
 */
internal class InterfaceMergerIr(
  private val classScanner: ClassScanner,
  private val moduleDescriptorFactory: RealAnvilModuleDescriptor.Factory,
) : IrGenerationExtension {
  // https://youtrack.jetbrains.com/issue/KT-56635
  override val shouldAlsoBeAppliedInKaptStubGenerationMode: Boolean get() = true

  override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
    moduleFragment.transform(
      object : IrElementTransformerVoid() {
        override fun visitClass(declaration: IrClass): IrStatement {
          if (declaration.shouldIgnore()) return super.visitClass(declaration)

          val mergeAnnotatedClass = declaration.symbol.toClassReference(pluginContext)

          val mergeAnnotations = mergeAnnotatedClass.annotations
            .findAll(mergeComponentFqName, mergeSubcomponentFqName, mergeInterfacesFqName)
            .ifEmpty { return super.visitClass(declaration) }

          if (!mergeAnnotatedClass.isInterface) {
            throw AnvilCompilationExceptionClassReferenceIr(
              classReference = mergeAnnotatedClass,
              message = "Dagger components must be interfaces.",
            )
          }

          addContributedInterfaces(
            mergeAnnotations = mergeAnnotations,
            moduleFragment = moduleFragment,
            pluginContext = pluginContext,
            mergeAnnotatedClass = mergeAnnotatedClass,
          )
          return super.visitClass(declaration)
        }
      },
      null,
    )
  }

  private fun addContributedInterfaces(
    mergeAnnotations: List<AnnotationReferenceIr>,
    moduleFragment: IrModuleFragment,
    pluginContext: IrPluginContext,
    mergeAnnotatedClass: ClassReferenceIr,
  ) {
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
            message = "${mergeAnnotatedClass.fqName} with scopes " +
              "${scopes.joinToString(prefix = "[", postfix = "]") { it.fqName.asString() }} " +
              "wants to exclude ${excludedClass.fqName}, but the excluded class isn't " +
              "contributed to the same scope.",
            classReference = mergeAnnotatedClass,
          )
        }
      }
      .toList()

    val supertypes = mergeAnnotatedClass.clazz.superTypes()
    if (excludedClasses.isNotEmpty()) {
      val intersect = supertypes
        .map { it.classOrFail.toClassReference(pluginContext) }
        .flatMap {
          it.allSuperTypeClassReferences(pluginContext, includeSelf = true)
        }
        .intersect(excludedClasses.toSet())

      if (intersect.isNotEmpty()) {
        throw AnvilCompilationExceptionClassReferenceIr(
          classReference = mergeAnnotatedClass,
          message = "${mergeAnnotatedClass.fqName} excludes types that it implements or " +
            "extends. These types cannot be excluded. Look at all the super types to find these " +
            "classes: ${intersect.joinToString { it.fqName.asString() }}.",
        )
      }
    }

    val supertypesToAdd = contributesAnnotations
      .asSequence()
      .map { it.declaringClass }
      .filter { clazz ->
        clazz !in replacedClasses && clazz !in excludedClasses
      }
      .plus(
        findContributedSubcomponentParentInterfaces(
          clazz = mergeAnnotatedClass,
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
    mergeAnnotatedClass.clazz.owner.superTypes += supertypesToAdd
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
          .generatedAnvilSubcomponent(clazz.classId)
          .createNestedClassId(Name.identifier(PARENT_COMPONENT))
          .referenceClassOrNull(pluginContext)
      }
  }
}

// TODO is this necessary in IR? Think all supertypes should already be resolved
internal fun ClassReferenceIr.allSuperTypeClassReferences(
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

internal fun ClassReferenceIr.atLeastOneAnnotation(
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
