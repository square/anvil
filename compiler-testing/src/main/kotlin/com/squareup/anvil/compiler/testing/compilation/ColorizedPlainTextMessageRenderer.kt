package com.squareup.anvil.compiler.testing.compilation

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.PlainTextMessageRenderer
import java.io.File
import kotlin.LazyThreadSafetyMode.NONE

internal class ColorizedPlainTextMessageRenderer : PlainTextMessageRenderer(true) {
  private val cwd by lazy(NONE) { File(".").absoluteFile }

  private val allMessages = mutableListOf<Pair<CompilerMessageSeverity, String>>()

  public fun messages(): CompilerMessages = CompilerMessages(allMessages)

  override fun getName(): String = "ColorizedRelativePath"

  override fun render(
    severity: CompilerMessageSeverity,
    message: String,
    location: CompilerMessageSourceLocation?,
  ): String = super.render(severity, message, location)
    .also {
      allMessages.add(severity to it)
    }

  override fun getPath(location: CompilerMessageSourceLocation): String {
    return File(location.path).toRelativeString(cwd)
  }
}

/**
 * Holds all messages sent through the
 * [MessageRenderer][org.jetbrains.kotlin.cli.common.messages.MessageRenderer]
 * in the corresponding compilation.
 */
public class CompilerMessages(public val all: List<Pair<CompilerMessageSeverity, String>>) {

  public val errors: List<String> by lazy(NONE) {
    all.filter { it.first.isError }.map { it.second }
  }
  public val warnings: List<String> by lazy(NONE) {
    all.filter { it.first.isWarning }.map { it.second }
  }
  public val infos: List<String> by lazy(NONE) {
    all.filter { it.first == CompilerMessageSeverity.INFO }.map { it.second }
  }
  public val logging: List<String> by lazy(NONE) {
    all.filter { it.first == CompilerMessageSeverity.LOGGING }.map { it.second }
  }
  public val output: List<String> by lazy(NONE) {
    all.filter { it.first == CompilerMessageSeverity.OUTPUT }.map { it.second }
  }

  public val hasErrors: Boolean get() = errors.isNotEmpty()
  public val hasWarnings: Boolean get() = warnings.isNotEmpty()
  public val isEmpty: Boolean get() = all.isEmpty()

  override fun toString(): String = all.joinToString("\n")
}
