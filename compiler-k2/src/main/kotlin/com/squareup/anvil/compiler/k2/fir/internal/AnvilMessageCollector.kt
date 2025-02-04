package com.squareup.anvil.compiler.k2.fir.internal

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.GroupingMessageCollector
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import java.io.Writer

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

public class MessageCollectorBackedWriter(
  private val messageCollector: MessageCollector,
  private val severity: CompilerMessageSeverity,
) : Writer() {
  override fun write(buffer: CharArray, offset: Int, length: Int) {
    val message = String(buffer, offset, length).trim().trim('\n', '\r')
    if (message.isNotEmpty()) {
      messageCollector.report(severity, message)
    }
  }

  override fun flush() {
    if (messageCollector is GroupingMessageCollector) {
      messageCollector.flush()
    }
  }

  override fun close() {
    flush()
  }
}
