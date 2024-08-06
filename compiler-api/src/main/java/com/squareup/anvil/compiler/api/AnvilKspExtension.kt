package com.squareup.anvil.compiler.api

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import java.util.ServiceLoader

/**
 * This interface helps custom KSP symbol processors that generate Anvil-relevant code to signal
 * their requirements to Anvil. [supportedAnnotationTypes] is used in contribution merging to know
 * when defer to a later round.
 *
 * For example, if you define a custom [SymbolProcessor] that generates code for `@MyAnnotation`,
 * you would implement [supportedAnnotationTypes] to return `setOf("com.example.MyAnnotation")`.
 * Then, during contribution merging, if Anvil KSP sees any symbols in that round annotated with
 * this annotation, it will defer to the next round to allow your processor to run first.
 *
 * You should only do this for classes that generate code that is also annotated with Anvil
 * annotations. It's not necessary for simple classes or dagger-only classes.
 *
 * The [AnvilKspExtension.Provider] interface is loaded via [java.util.ServiceLoader] and you
 * should package a service file for your implementations accordingly. As such, order of execution
 * is **not** guaranteed. You should _not_ contribute implementations of [AnvilKspExtension] via
 * [ServiceLoader] directly, these should only be created when [AnvilKspExtension.Provider.create]
 * is called.
 */
public interface AnvilKspExtension {
  public interface Provider : AnvilApplicabilityChecker {
    /**
     * Called by Anvil KSP to create the extension.
     */
    public fun create(environment: SymbolProcessorEnvironment): AnvilKspExtension
  }

  /** Returns the set of annotation types that this extension supports. */
  public val supportedAnnotationTypes: Set<String>

  /**
   * Called by Anvil KSP to run the processing task.
   *
   * @param resolver provides [SymbolProcessor] with access to compiler details such as Symbols.
   * @return A list of deferred symbols that the processor can't process. Only symbols that can't be processed at this round should be returned. Symbols in compiled code (libraries) are always valid and are ignored if returned in the deferral list.
   */
  public fun process(resolver: Resolver): List<KSAnnotated>
}
