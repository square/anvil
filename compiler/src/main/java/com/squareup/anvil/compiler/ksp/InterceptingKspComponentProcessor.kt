package com.squareup.anvil.compiler.ksp

import com.google.auto.service.AutoService
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.processing.impl.ResolverImpl
import com.google.devtools.ksp.symbol.KSAnnotated
import com.squareup.anvil.compiler.ClassScanner
import com.squareup.anvil.compiler.codegen.reference.RealAnvilModuleDescriptor
import dagger.internal.codegen.KspComponentProcessor
import org.jetbrains.kotlin.name.FqName

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
    if (annotationName !in KspAnnotations.annotationNames) {
      return delegate.getSymbolsWithAnnotation(annotationName, inDepth)
    }

    // Dagger's asking for a mergable annotation. Intercept and go look up
    // components with the corresponding merge analogue.
    val mergeAnnotationName = KspAnnotations.annotationMapping[annotationName]
      ?: error("Expected to find a corresponding merge annotation for $annotationName")

    val symbols = delegate.getSymbolsWithAnnotation(mergeAnnotationName, inDepth)

    val mapper: MergeAnnotationMapper = PsiMergeAnnotationMapper(
      module,
      classScanner,
      this,
      FqName(mergeAnnotationName),
    )

    // Dagger's asking for a mergeable annotation, remap them with contributed bindings
    return symbols.map { mapper.remapAnnotated(it) }
  }
}
