package com.squareup.anvil.compiler

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.GroupingMessageCollector
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import java.io.Writer

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
