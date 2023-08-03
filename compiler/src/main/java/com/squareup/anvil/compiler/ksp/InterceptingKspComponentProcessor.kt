package com.squareup.anvil.compiler.ksp

import com.google.auto.service.AutoService
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.isLocal
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.processing.impl.KSNameImpl
import com.google.devtools.ksp.processing.impl.ResolverImpl
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
import com.squareup.anvil.compiler.mergeModulesFqName
import com.squareup.anvil.compiler.mergeSubcomponentFqName
import dagger.internal.codegen.KspComponentProcessor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.types.SimpleType

/**
 * An intercepting [SymbolProcessor] that intercepts [process] calls
 * to a [KspComponentProcessor] with a [InterceptingResolver] that adds merged
 * Dagger modules and component interfaces to returned annotated elements.
 */
internal class InterceptingKspComponentProcessor(
  private val delegate: SymbolProcessor,
) : SymbolProcessor {

  override fun process(resolver: Resolver) =
    delegate.process(InterceptingResolver(resolver))

  @AutoService(SymbolProcessorProvider::class)
  class Provider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment) =
      InterceptingKspComponentProcessor(
        KspComponentProcessor.Provider().create(environment)
      )
  }
}

/**
 * A [Resolver] that intercepts calls to mergeable annotated symbols (e.g. `@Component`, `@Module`,
 * etc.), and rewrites them to add merged information.
 */
private class InterceptingResolver(private val delegate: Resolver) : Resolver by delegate {
  private val module by lazy {
    val delegateModule =
      (delegate as? ResolverImpl)?.module ?: error("Could not load ModuleDescriptor")
    // TODO share this factory instance somewhere?
    RealAnvilModuleDescriptor.Factory()
      .create(delegateModule)
  }
  private val classScanner = ClassScanner()

  override fun getSymbolsWithAnnotation(
    annotationName: String,
    inDepth: Boolean
  ): Sequence<KSAnnotated> {
    val symbols = delegate.getSymbolsWithAnnotation(annotationName, inDepth)
    if (annotationName !in mergeAnnotationNames) return symbols
    val annotationFqName = FqName(annotationName)

    // Dagger's asking for a mergable annotation, remap them with contributed bindings
    return symbols.map { remapAnnotated(annotationFqName, it) }
  }

  private fun remapAnnotated(
    daggerAnnotationName: FqName,
    annotated: KSAnnotated,
  ): KSAnnotated {
    check(annotated is KSClassDeclaration) { "Expected KSClassDeclaration, got $annotated" }
    if (annotated.shouldIgnore()) return annotated
    val clazz = module.getClassReferenceOrNull(annotated.fqName) ?: return annotated
    val annotationClassReference =
      module.getClassReferenceOrNull(daggerAnnotationName) ?: return annotated

    return annotated.processMergeAnnotations(
      annotationClassReference = annotationClassReference,
      clazz = clazz,
    ).processMergeInterfaces(
      annotationClassReference = annotationClassReference,
      clazz = clazz,
    )
  }

  private fun KSClassDeclaration.processMergeInterfaces(
    annotationClassReference: ClassReference,
    clazz: ClassReference,
  ): KSClassDeclaration {
    val mergeAnnotations = clazz.annotations
      .findAll(mergeComponentFqName, mergeSubcomponentFqName, mergeInterfacesFqName)
      .ifEmpty { return this@processMergeInterfaces }

    val result = InterfaceMerger.mergeInterfaces(
      classScanner = classScanner,
      module = module,
      mergeAnnotatedClass = annotationClassReference,
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
        it.annotationType.resolve().declaration.qualifiedName?.asString() in mergeAnnotationNames
      }
      .toList()
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
      ModuleKSAnnotation(filteredMergeAnnotations[0], mergedModules, delegate)

    return replaceAnnotations(newAnnotations.toList())
  }

  private companion object {
    private val mergeModulesName = mergeModulesFqName.asString()
    private val mergeComponentName = mergeComponentFqName.asString()
    private val mergeSubcomponentName = mergeSubcomponentFqName.asString()
    private val mergeAnnotationNames =
      setOf(mergeModulesName, mergeComponentName, mergeSubcomponentName)
  }
}

private fun KSAnnotation.toAnnotationReference(
  classReference: ClassReference,
  declaringClass: ClassReference
): AnnotationReference {
  return when (this) {
    is KSAnnotationDescriptorImpl -> {
      check(classReference is ClassReference.Descriptor)
      descriptor.toAnnotationReference(
        classReference,
        declaringClass
      )
    }

    is KSAnnotationImpl -> {
      check(classReference is ClassReference.Psi)
      ktAnnotationEntry.toAnnotationReference(
        classReference,
        declaringClass
      )
    }

    is KSAnnotationJavaImpl -> {
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
