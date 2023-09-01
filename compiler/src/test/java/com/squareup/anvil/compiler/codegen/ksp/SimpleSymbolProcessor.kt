package com.squareup.anvil.compiler.codegen.ksp

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSFile
import com.squareup.anvil.compiler.api.AnvilApplicabilityChecker
import com.squareup.anvil.compiler.internal.testing.parseSimpleFileContents
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

internal fun simpleSymbolProcessor(
  mapper: AnvilSymbolProcessor.(resolver: Resolver, env: SymbolProcessorEnvironment) -> List<SimpleMapperResult>
): SymbolProcessorProvider = AnvilSymbolProcessorProvider(
  AnvilApplicabilityChecker.always()
) { env -> SimpleSymbolProcessor(env, mapper) }

private class SimpleSymbolProcessor(
  override val env: SymbolProcessorEnvironment,
  private val mapper: AnvilSymbolProcessor.(resolver: Resolver, env: SymbolProcessorEnvironment) -> List<SimpleMapperResult>
) : AnvilSymbolProcessor() {
  override fun processChecked(resolver: Resolver): List<KSAnnotated> {
    this.mapper(resolver, env)
      .map { result ->
        env.logger.info("Content received")
        val (packageName, fileName) = parseSimpleFileContents(result.content)

        val dependencies = Dependencies(
          aggregating = false,
          sources = listOfNotNull(result.originatingFile).toTypedArray()
        )
        val file = env.codeGenerator.createNewFile(dependencies, packageName, fileName)

        // Don't use writeTo(file) because that tries to handle directories under the hood
        OutputStreamWriter(file, StandardCharsets.UTF_8)
          .buffered()
          .use { writer ->
            writer.write(result.content)
            writer.flush()
            env.logger.info("Content written to file")
          }
      }
      .toList()
    return emptyList()
  }
}

internal data class SimpleMapperResult(
  val content: String,
  val originatingFile: KSFile?,
)
