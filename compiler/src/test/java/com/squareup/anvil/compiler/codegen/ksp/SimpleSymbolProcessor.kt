package com.squareup.anvil.compiler.codegen.ksp

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.squareup.anvil.compiler.api.AnvilApplicabilityChecker
import com.squareup.anvil.compiler.internal.testing.parseSimpleFileContents
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

internal fun simpleSymbolProcessor(
  mapper: AnvilSymbolProcessor.(resolver: Resolver) -> List<String>,
): SymbolProcessorProvider = AnvilSymbolProcessorProvider(
  AnvilApplicabilityChecker.always(),
) { env -> SimpleSymbolProcessor(env, mapper) }

private class SimpleSymbolProcessor(
  override val env: SymbolProcessorEnvironment,
  private val mapper: AnvilSymbolProcessor.(resolver: Resolver) -> List<String>,
) : AnvilSymbolProcessor() {
  override fun processChecked(resolver: Resolver): List<KSAnnotated> {
    this.mapper(resolver)
      .map { content ->
        val (packageName, fileName) = parseSimpleFileContents(content)

        val dependencies = Dependencies(aggregating = false, sources = emptyArray())
        val file = env.codeGenerator.createNewFile(dependencies, packageName, "$fileName.kt")
        // Don't use writeTo(file) because that tries to handle directories under the hood
        OutputStreamWriter(file, StandardCharsets.UTF_8)
          .buffered()
          .use { writer ->
            writer.write(content)
          }
      }
      .toList()
    return emptyList()
  }
}
