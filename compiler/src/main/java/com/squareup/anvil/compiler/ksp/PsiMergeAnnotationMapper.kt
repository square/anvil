package com.squareup.anvil.compiler.ksp

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.isLocal
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.impl.KSNameImpl
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSReferenceElement
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.KSValueArgument
import com.google.devtools.ksp.symbol.KSVisitor
import com.google.devtools.ksp.symbol.Location
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.NonExistLocation
import com.google.devtools.ksp.symbol.Origin
import com.google.devtools.ksp.symbol.impl.binary.KSAnnotationDescriptorImpl
import com.google.devtools.ksp.symbol.impl.java.KSAnnotationJavaImpl
import com.google.devtools.ksp.symbol.impl.kotlin.KSAnnotationImpl
import com.google.devtools.ksp.symbol.impl.kotlin.KSTypeImpl
import com.google.devtools.ksp.symbol.impl.kotlin.KSValueArgumentLiteImpl
import com.squareup.anvil.compiler.ClassScanner
import com.squareup.anvil.compiler.InterfaceMerger
import com.squareup.anvil.compiler.ModuleMerger
import com.squareup.anvil.compiler.codegen.findAll
import com.squareup.anvil.compiler.codegen.reference.RealAnvilModuleDescriptor
import com.squareup.anvil.compiler.internal.reference.AnnotationReference
import com.squareup.anvil.compiler.internal.reference.ClassReference
import com.squareup.anvil.compiler.internal.reference.toAnnotationReference
import com.squareup.anvil.compiler.mergeComponentFqName
import com.squareup.anvil.compiler.mergeInterfacesFqName
import com.squareup.anvil.compiler.mergeSubcomponentFqName
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.types.SimpleType

internal class PsiMergeAnnotationMapper(
  private val module: RealAnvilModuleDescriptor,
  private val classScanner: ClassScanner,
  private val resolver: Resolver,
  private val daggerAnnotationName: FqName,
) : MergeAnnotationMapper() {

  override fun remapAnnotated(annotated: KSAnnotated): KSAnnotated {
    check(annotated is KSClassDeclaration) { "Expected KSClassDeclaration, got $annotated" }
    if (annotated.shouldIgnore()) return annotated
    val clazz = module.getClassReferenceOrNull(annotated.fqName) ?: return annotated
    val annotationClassReference =
      module.getClassReferenceOrNull(daggerAnnotationName) ?: return annotated

    return annotated.processMergeAnnotations(
      annotationClassReference = annotationClassReference,
      clazz = clazz,
    ).processMergeInterfaces(clazz)
  }

  private fun KSClassDeclaration.processMergeInterfaces(
    clazz: ClassReference,
  ): KSClassDeclaration {
    val mergeAnnotations = clazz.annotations
      .findAll(mergeComponentFqName, mergeSubcomponentFqName, mergeInterfacesFqName)
      .ifEmpty { return this@processMergeInterfaces }

    val result = InterfaceMerger.mergeInterfaces(
      classScanner = classScanner,
      module = module,
      mergeAnnotatedClass = clazz,
      annotations = mergeAnnotations,
      supertypes = clazz.directSuperTypeReferences()
        .map { it.asClassReference() }
    )

    val newSupertypes = result.contributesAnnotations
      .asSequence()
      .map { it.declaringClass() }
      .filter { declaringClass ->
        declaringClass !in result.replacedClasses && declaringClass !in result.excludedClasses
      }
      .plus(result.contributedSubcomponentInterfaces())
      // Avoids an error for repeated interfaces.
      .distinct()
      .map { (it as ClassReference.Descriptor).clazz.defaultType }
      .map { it.toKsTypeReference() }
      .toList()

    if (newSupertypes.isEmpty()) {
      return this@processMergeInterfaces
    }

    // Remap the class to include the new supertypes
    val delegate = this
    val newSuperTypes = delegate.superTypes + newSupertypes
    return object : KSClassDeclaration by delegate {
      override val superTypes get() = newSuperTypes
    }
  }

  private fun SimpleType.toKsTypeReference(): KSTypeReference {
    return NoLocationTypeReference(
      // TODO what about ksTypeArguments and annotations?
      KSTypeImpl.getCached(this)
    )
  }

  private class NoLocationTypeReference(
    val resolved: KSType
  ) : KSTypeReference {
    override val annotations: Sequence<KSAnnotation>
      get() = emptySequence()
    override val element: KSReferenceElement?
      get() = null
    override val location: Location
      get() = NonExistLocation
    override val modifiers: Set<Modifier>
      get() = emptySet()
    override val origin: Origin
      get() = Origin.SYNTHETIC
    override val parent: KSNode?
      get() = null

    override fun <D, R> accept(visitor: KSVisitor<D, R>, data: D): R {
      return visitor.visitTypeReference(this, data)
    }

    override fun resolve(): KSType = resolved
  }

  private fun KSClassDeclaration.processMergeAnnotations(
    annotationClassReference: ClassReference,
    clazz: ClassReference,
  ): KSClassDeclaration {
    val filteredMergeAnnotations = annotations
      .filter {
        it.annotationType.resolve().declaration.qualifiedName?.asString() in
          KspAnnotations.mergeAnnotationNames
      }
      .toList()
    check(filteredMergeAnnotations.isNotEmpty()) {
      "Expected to find at least one @MergeComponent annotation on $clazz"
    }
    val mergeAnnotationReferences = filteredMergeAnnotations
      .map { it.toAnnotationReference(annotationClassReference, clazz) }
      .toList()
    if (mergeAnnotationReferences.isEmpty()) {
      return this
    }

    val mergedModules = ModuleMerger.mergeModules(
      classScanner,
      module,
      clazz,
      mergeAnnotationReferences,
    )
    val newAnnotations = annotations +
      ModuleKSAnnotation(filteredMergeAnnotations[0], mergedModules, resolver)

    return replaceAnnotations(newAnnotations.toList())
  }
}

private fun KSAnnotation.toAnnotationReference(
  classReference: ClassReference,
  declaringClass: ClassReference
): AnnotationReference {
  return when (this) {
    is KSAnnotationDescriptorImpl -> {
      descriptor.toAnnotationReference(
        declaringClass = declaringClass,
        classReference = classReference,
      )
    }

    is KSAnnotationImpl -> {
      ktAnnotationEntry.toAnnotationReference(
        declaringClass = declaringClass,
        classReference = classReference,
      )
    }

    is KSAnnotationJavaImpl -> {
      // TODO implement PsiAnnotation.toAnnotationReference()?
      error("Java annotations are not supported: $this")
    }

    else -> error("Unrecognized KSAnnotation impl type: $javaClass")
  }
}

/**
 * Returns a new [KSClassDeclaration] with its [KSClassDeclaration.annotations] replaced
 * with [newAnnotations].
 */
private fun KSClassDeclaration.replaceAnnotations(
  newAnnotations: List<KSAnnotation>
): KSClassDeclaration {
  return object : KSClassDeclaration by this {
    override val annotations: Sequence<KSAnnotation> get() = newAnnotations.asSequence()
  }
}

// If we're evaluating an anonymous inner class, it cannot merge anything and will cause
// a failure if we try to resolve its [ClassId]
private fun KSDeclaration.shouldIgnore(): Boolean {
  return qualifiedName == null || isLocal()
}

private class ModuleKSAnnotation(
  private val delegate: KSAnnotation,
  private val mergedModules: Set<ClassReference>,
  private val resolver: Resolver,
) : KSAnnotation by delegate {
  override val arguments: List<KSValueArgument>
    get() = listOf(
      KSValueArgumentLiteImpl.getCached(
        name = KSNameImpl.getCached("modules"),
        value = listOf(
          mergedModules.map { type ->
            resolver.getClassDeclarationByName(type.fqName.asString())!!.asType(emptyList())
          }
        ),
        parent = this,
        origin = origin
      )
    )
  override val defaultArguments: List<KSValueArgument>
    get() = emptyList()
}
