package com.squareup.anvil.compiler.ksp

import com.google.auto.service.AutoService
import com.google.devtools.ksp.isLocal
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.processing.impl.ResolverImpl
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSName
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.KSValueArgument
import com.google.devtools.ksp.symbol.impl.binary.KSAnnotationDescriptorImpl
import com.google.devtools.ksp.symbol.impl.java.KSAnnotationJavaImpl
import com.google.devtools.ksp.symbol.impl.kotlin.KSAnnotationImpl
import com.squareup.anvil.compiler.ClassScanner
import com.squareup.anvil.compiler.ModuleMerger
import com.squareup.anvil.compiler.ModuleMerger.MergeResult
import com.squareup.anvil.compiler.codegen.reference.RealAnvilModuleDescriptor
import com.squareup.anvil.compiler.internal.reference.AnnotationReference
import com.squareup.anvil.compiler.internal.reference.ClassReference
import com.squareup.anvil.compiler.internal.reference.toAnnotationReference
import com.squareup.anvil.compiler.mergeComponentFqName
import com.squareup.anvil.compiler.mergeModulesFqName
import com.squareup.anvil.compiler.mergeSubcomponentFqName
import dagger.internal.codegen.KspComponentProcessor
import org.jetbrains.kotlin.name.FqName

class AnvilSymbolProcessor(
  private val delegate: SymbolProcessor,
) : SymbolProcessor {

  override fun process(resolver: Resolver): List<KSAnnotated> {
    return delegate.process(DecoratedResolver(resolver))
  }

  @AutoService(SymbolProcessorProvider::class)
  class Provider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
      val delegate = KspComponentProcessor.Provider().create(environment)
      return AnvilSymbolProcessor(delegate)
    }
  }
}

/**
 * A [Resolver] that intercepts calls to mergeable annotated symbols (e.g. `@Component`, `@Module`,
 * etc.), and rewrites them to add merged information.
 */
class DecoratedResolver(private val delegate: Resolver) : Resolver by delegate {
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
    // TODO
    return this
  }

  private fun KSClassDeclaration.processMergeAnnotations(
    annotationClassReference: ClassReference,
    clazz: ClassReference,
  ): KSClassDeclaration {
    val filteredMergeAnnotations = annotations
      .filter { it.annotationType.resolve().declaration.qualifiedName?.asString() in mergeAnnotationNames }
      .toList()
    val mergeAnnotationReferences = filteredMergeAnnotations
      .map { it.toAnnotationReference(annotationClassReference, clazz) }
      .toList()
    if (mergeAnnotationReferences.isEmpty()) {
      return this
    }

    val moduleMergeResult = ModuleMerger.mergeModules(
      classScanner,
      module,
      clazz,
      mergeAnnotationReferences,
    )
    val newAnnotations = annotations + moduleMergeResult
      .toModuleAnnotation(annotationClassReference, filteredMergeAnnotations[0])

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
 * Returns a new [KSClassDeclaration] with its [KSClassDeclaration.annotations] replaced with [newAnnotations].
 */
private fun KSClassDeclaration.replaceAnnotations(newAnnotations: List<KSAnnotation>): KSClassDeclaration {
  return object : KSClassDeclaration by this {
    override val annotations: Sequence<KSAnnotation> get() = newAnnotations.asSequence()
  }
}

// If we're evaluating an anonymous inner class, it cannot merge anything and will cause
// a failure if we try to resolve its [ClassId]
private fun KSDeclaration.shouldIgnore(): Boolean {
  return qualifiedName == null || isLocal()
}

// TODO implement this
private fun MergeResult.toModuleAnnotation(
  classReference: ClassReference,
  delegate: KSAnnotation,
): KSAnnotation {
  return object : KSAnnotation by delegate {
    override val annotationType: KSTypeReference
      get() = TODO("Not yet implemented")
    override val arguments: List<KSValueArgument>
      get() = TODO("Not yet implemented")
    override val defaultArguments: List<KSValueArgument>
      get() = TODO("Not yet implemented")
    override val shortName: KSName
      get() = TODO("Not yet implemented")
  }
}
