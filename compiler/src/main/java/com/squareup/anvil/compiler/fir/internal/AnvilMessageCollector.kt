package com.squareup.anvil.compiler.fir.internal

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer

internal class AnvilMessageCollector(
  private val messageRenderer: MessageRenderer,
) : MessageCollector {
  override fun clear() {
  }

  override fun hasErrors(): Boolean = false

  override fun report(
    severity: CompilerMessageSeverity,
    message: String,
    location: CompilerMessageSourceLocation?,
  ) {

    val rendered = messageRenderer.render(severity, message, location)

    println(rendered)

    // when (severity) {
    //   CompilerMessageSeverity.EXCEPTION -> TODO()
    //   CompilerMessageSeverity.ERROR -> TODO()
    //   CompilerMessageSeverity.STRONG_WARNING -> TODO()
    //   CompilerMessageSeverity.WARNING -> TODO()
    //   CompilerMessageSeverity.INFO -> TODO()
    //   CompilerMessageSeverity.LOGGING -> TODO()
    //   CompilerMessageSeverity.OUTPUT -> TODO()
    // }
  }
}
